package de.quarzee.realterrain.explorer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySamplerSet;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/** Local, read-only world preview generated from the active world's samplers. */
public final class TerrainExplorerServer {
    private static final Gson GSON = new Gson();
    private static final int HALF_FOOTPRINT = 250_000;
    private static final int DEFAULT_SIZE = 256;
    private static final int MAX_SIZE = 512;
    private static final Map<String, Integer> BIOME_COLORS = createBiomeColors();

    private static volatile HttpServer server;
    private static volatile ServerLevel level;

    private TerrainExplorerServer() {}

    public static synchronized String start(ServerLevel targetLevel) throws IOException {
        if (server != null) {
            level = targetLevel;
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        HttpServer created = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        created.createContext("/", TerrainExplorerServer::handleIndex);
        created.createContext("/api/status", TerrainExplorerServer::handleStatus);
        created.createContext("/api/map.png", TerrainExplorerServer::handleMap);
        created.createContext("/api/point", TerrainExplorerServer::handlePoint);
        created.setExecutor(Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "realterrain-map-http");
            thread.setDaemon(true);
            return thread;
        }));
        level = targetLevel;
        created.start();
        server = created;
        return "http://127.0.0.1:" + created.getAddress().getPort();
    }

    public static synchronized void stop() {
        HttpServer running = server;
        server = null;
        level = null;
        if (running != null) running.stop(0);
    }

    private static void handleIndex(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!exchange.getRequestURI().getPath().equals("/")) {
                sendText(exchange, 404, "Not found");
                return;
            }
            try (InputStream input = TerrainExplorerServer.class.getResourceAsStream("/assets/realterrain/explorer/index.html")) {
                if (input == null) {
                    sendText(exchange, 404, "Map page not found in the mod resources.");
                    return;
                }
                byte[] body = input.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        }
    }

    private static void handleMap(HttpExchange exchange) throws IOException {
        try {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendText(exchange, 405, "GET required");
                return;
            }
            ServerLevel target = level;
            if (target == null) {
                sendText(exchange, 503, "The world is no longer available.");
                return;
            }
            int size = Math.max(64, Math.min(MAX_SIZE, parseInt(query(exchange).get("size"), DEFAULT_SIZE)));
            String mode = query(exchange).getOrDefault("mode", "biome");
            if (!mode.equals("biome") && !mode.equals("height") && !mode.equals("temperature")) mode = "biome";
            BufferedImage image = renderMap(target, size, mode);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            byte[] body = output.toByteArray();
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-World-Seed", Long.toString(target.getSeed()));
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        } catch (Exception exception) {
            sendText(exchange, 500, "Could not create map: " + exception.getMessage());
        } finally {
            exchange.close();
        }
    }

    private static void handleStatus(HttpExchange exchange) throws IOException {
        try {
            ServerLevel target = level;
            if (target == null) {
                sendText(exchange, 503, "The world is no longer available.");
                return;
            }
            JsonObject result = new JsonObject();
            result.addProperty("seed", Long.toString(target.getSeed()));
            result.addProperty("halfFootprint", HALF_FOOTPRINT);
            sendJson(exchange, 200, GSON.toJson(result));
        } finally {
            exchange.close();
        }
    }

    private static void handlePoint(HttpExchange exchange) throws IOException {
        try {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendText(exchange, 405, "GET required");
                return;
            }
            Map<String, String> query = query(exchange);
            int x = parseInt(query.get("x"), 0);
            int z = parseInt(query.get("z"), 0);
            if (Math.abs(x) > HALF_FOOTPRINT || Math.abs(z) > HALF_FOOTPRINT) {
                sendText(exchange, 400, "Coordinates must be inside the 500,000 by 500,000 block footprint.");
                return;
            }
            ServerLevel target = level;
            if (target == null) {
                sendText(exchange, 503, "The world is no longer available.");
                return;
            }
            Sample sample = sample(target, x, z);
            JsonObject result = new JsonObject();
            result.addProperty("x", x);
            result.addProperty("z", z);
            result.addProperty("surfaceY", sample.surfaceY());
            result.addProperty("biome", sample.biomeId());
            result.addProperty("temperature", sample.temperature());
            sendJson(exchange, 200, GSON.toJson(result));
        } catch (Exception exception) {
            sendText(exchange, 500, "Could not sample location: " + exception.getMessage());
        } finally {
            exchange.close();
        }
    }

    private static BufferedImage renderMap(ServerLevel level, int size, String mode) {
        NoiseBasedChunkGenerator generator = requireGenerator(level);
        NoiseGeneratorSettings settings = generator.generatorSettings().value();
        DensityFunction density = settings.noiseRouter().finalDensity();
        RandomState randomState = level.getChunkSource().randomState();
        SamplerContext context = SamplerContext.builder().enableCaches().build();
        DensitySamplerSet densitySamplers = randomState.samplersWithContext(context);
        Climate.Sampler climateSampler = randomState.createClimateSampler(context);
        BiomeResolver resolver = generator.getBiomeSource().createResolver(climateSampler);
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        int[][] colors = new int[size][size];
        int[][] heights = new int[size][size];
        float[][] temperatures = new float[size][size];
        double blockStep = (2.0 * HALF_FOOTPRINT) / (size - 1);

        for (int py = 0; py < size; py++) {
            int z = coordinate(py, size);
            for (int px = 0; px < size; px++) {
                int x = coordinate(px, size);
                int surfaceY = findSurfaceY(densitySamplers, density, generator.getMinY(),
                        generator.getMinY() + generator.getGenDepth() - 1, x, z);
                int biomeY = Math.max(surfaceY, generator.getSeaLevel());
                int quartX = QuartPos.fromBlock(x);
                int quartY = QuartPos.fromBlock(biomeY);
                int quartZ = QuartPos.fromBlock(z);
                Climate.TargetPoint climate = climateSampler.sample(quartX, quartY, quartZ);
                Holder<Biome> biome = resolver.getNoiseBiome(quartX, quartY, quartZ);
                String id = biome.unwrapKey().map(ResourceKey::identifier).map(location -> location.getPath()).orElse("");
                heights[py][px] = surfaceY;
                colors[py][px] = BIOME_COLORS.getOrDefault(id, 0x477E48);
                temperatures[py][px] = Climate.unquantizeCoord(climate.temperature());
            }
        }

        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                int left = heights[py][Math.max(0, px - 1)];
                int right = heights[py][Math.min(size - 1, px + 1)];
                int north = heights[Math.max(0, py - 1)][px];
                int south = heights[Math.min(size - 1, py + 1)][px];
                double slope = ((left - right) + (north - south)) / (blockStep * 1.25);
                double light = Mth.clamp(0.78 + slope * 0.26, 0.48, 1.12);
                int color = switch (mode) {
                    case "height" -> heightColor(heights[py][px], generator.getSeaLevel());
                    case "temperature" -> temperatureColor(temperatures[py][px]);
                    default -> colors[py][px];
                };
                int red = (int) (((color >> 16) & 255) * light);
                int green = (int) (((color >> 8) & 255) * light);
                int blue = (int) ((color & 255) * light);
                image.setRGB(px, py, (Mth.clamp(red, 0, 255) << 16) | (Mth.clamp(green, 0, 255) << 8) | Mth.clamp(blue, 0, 255));
            }
        }
        return image;
    }

    private static Sample sample(ServerLevel level, int x, int z) {
        NoiseBasedChunkGenerator generator = requireGenerator(level);
        NoiseGeneratorSettings settings = generator.generatorSettings().value();
        RandomState randomState = level.getChunkSource().randomState();
        SamplerContext context = SamplerContext.builder().enableCaches().build();
        DensitySamplerSet densitySamplers = randomState.samplersWithContext(context);
        Climate.Sampler climateSampler = randomState.createClimateSampler(context);
        DensityFunction density = settings.noiseRouter().finalDensity();
        int surfaceY = findSurfaceY(densitySamplers, density, generator.getMinY(),
                generator.getMinY() + generator.getGenDepth() - 1, x, z);
        int biomeY = Math.max(surfaceY, generator.getSeaLevel());
        int quartX = QuartPos.fromBlock(x);
        int quartY = QuartPos.fromBlock(biomeY);
        int quartZ = QuartPos.fromBlock(z);
        Climate.TargetPoint climate = climateSampler.sample(quartX, quartY, quartZ);
        Holder<Biome> biome = generator.getBiomeSource().createResolver(climateSampler).getNoiseBiome(quartX, quartY, quartZ);
        String id = biome.unwrapKey().map(ResourceKey::identifier).map(Object::toString).orElse("unknown");
        return new Sample(surfaceY, id, Climate.unquantizeCoord(climate.temperature()));
    }

    private static int heightColor(int height, int seaLevel) {
        if (height <= seaLevel) {
            double depth = Mth.clamp((height + 64.0) / (seaLevel + 64.0), 0.0, 1.0);
            return blend(0x001B48, 0x005FFF, depth);
        }
        int tenBlockBands = (height - seaLevel) / 10;
        float hue = Math.max(0.0F, 216.0F - tenBlockBands * 2.0F) / 360.0F;
        return Mth.hsvToRgb(hue, 1.0F, 1.0F) & 0xFFFFFF;
    }

    private static int temperatureColor(float temperature) {
        if (temperature < -0.1F) return blend(0x2537A4, 0x48C8E5, (temperature + 1.0) / 0.9);
        if (temperature < 0.35F) return blend(0x48C8E5, 0xF0DB67, (temperature + 0.1) / 0.45);
        return blend(0xF0DB67, 0xD43B24, (temperature - 0.35) / 0.65);
    }

    private static int blend(int from, int to, double amount) {
        double t = Mth.clamp(amount, 0.0, 1.0);
        int r = (int) (((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * t);
        int g = (int) (((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * t);
        int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static int findSurfaceY(DensitySamplerSet samplers, DensityFunction density, int minY, int maxY, int x, int z) {
        int low = minY;
        int high = maxY;
        while (low < high) {
            int mid = low + (high - low + 1) / 2;
            if (samplers.sampleValue(density, x, mid, z) > 0.0F) low = mid;
            else high = mid - 1;
        }
        return low;
    }

    private static NoiseBasedChunkGenerator requireGenerator(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) {
            throw new IllegalStateException("This world's generator does not support a RealTerrain map.");
        }
        return generator;
    }

    private static int coordinate(int pixel, int size) {
        return (int) Math.round(-HALF_FOOTPRINT + (2.0 * HALF_FOOTPRINT * pixel) / (size - 1));
    }

    private static Map<String, Integer> createBiomeColors() {
        Map<String, Integer> colors = new HashMap<>();
        colors.put("tropical_rainforest", 0x126B3A);
        colors.put("tropical_seasonal_forest", 0x398C43);
        colors.put("savanna", 0xC1A83D);
        colors.put("hot_desert", 0xD9AC59);
        colors.put("mediterranean_woodland", 0x728D4E);
        colors.put("temperate_grassland", 0x89B65B);
        colors.put("temperate_deciduous_forest", 0x34774A);
        colors.put("temperate_rainforest", 0x205B52);
        colors.put("cold_desert_steppe", 0xAAA581);
        colors.put("boreal_forest", 0x43664D);
        colors.put("tundra", 0xA2B4A6);
        colors.put("polar_desert", 0xE9F0EC);
        colors.put("ice_sea", 0xB8E7F3);
        colors.put("warm_ocean", 0x40B9A7);
        colors.put("deep_warm_ocean", 0x17627B);
        colors.put("lukewarm_ocean", 0x42AFA2);
        colors.put("deep_lukewarm_ocean", 0x1C687F);
        colors.put("ocean", 0x4F9EA8);
        colors.put("deep_ocean", 0x244F77);
        colors.put("cold_ocean", 0x568FAD);
        colors.put("deep_cold_ocean", 0x244A75);
        colors.put("frozen_ocean", 0x8ABEC9);
        colors.put("deep_frozen_ocean", 0x416F8C);
        return Map.copyOf(colors);
    }

    private static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> result = new HashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null) return result;
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
        }
        return result;
    }

    private static int parseInt(String value, int fallback) {
        try { return value == null ? fallback : Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendText(HttpExchange exchange, int status, String body) throws IOException {
        send(exchange, status, "text/plain; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private record Sample(int surfaceY, String biomeId, float temperature) {}
}
