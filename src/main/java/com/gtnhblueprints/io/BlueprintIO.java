package com.gtnhblueprints.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.gtnhblueprints.BlueprintConfig;
import com.gtnhblueprints.model.Blueprint;

public final class BlueprintIO {

    private BlueprintIO() {}

    public static byte[] encode(Blueprint blueprint) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes);
            Writer writer = new OutputStreamWriter(gzip, StandardCharsets.UTF_8)) {
            BlueprintJson.GSON.toJson(blueprint, writer);
        }
        byte[] encoded = bytes.toByteArray();
        if (encoded.length > BlueprintConfig.maxTransferBytes) throw new IOException("压缩后的蓝图超过传输限制");
        return encoded;
    }

    public static Blueprint decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > BlueprintConfig.maxTransferBytes) {
            throw new IOException("蓝图文件大小无效");
        }
        long decodedLimit = Math.min(256L * 1024 * 1024, Math.max(4L * 1024 * 1024, bytes.length * 32L));
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes));
            InputStream limited = new LimitedInputStream(gzip, decodedLimit);
            Reader reader = new InputStreamReader(limited, StandardCharsets.UTF_8)) {
            Blueprint blueprint = BlueprintJson.GSON.fromJson(reader, Blueprint.class);
            if (blueprint == null) throw new IOException("蓝图内容为空");
            return blueprint;
        } catch (RuntimeException exception) {
            throw new IOException("蓝图 JSON 无效", exception);
        }
    }

    public static File resolve(File directory, String name) throws IOException {
        String safe = safeName(name);
        File file = new File(directory, safe + ".gtbp").getCanonicalFile();
        File canonicalDirectory = directory.getCanonicalFile();
        if (!file.getParentFile().equals(canonicalDirectory)) throw new IOException("蓝图文件名越界");
        return file;
    }

    public static void write(File directory, String name, byte[] bytes) throws IOException {
        Files.createDirectories(directory.toPath());
        File destination = resolve(directory, name);
        File temporary = new File(directory, destination.getName() + ".tmp");
        Files.write(temporary.toPath(), bytes);
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static byte[] read(File directory, String name) throws IOException {
        File file = resolve(directory, name);
        long size = Files.size(file.toPath());
        if (size <= 0 || size > BlueprintConfig.maxTransferBytes) throw new IOException("蓝图文件大小无效: " + size);
        return Files.readAllBytes(file.toPath());
    }

    public static List<String> list(File directory) {
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".gtbp"));
        if (files == null) return Collections.emptyList();
        List<String> names = new ArrayList<>(files.length);
        for (File file : files) names.add(file.getName().substring(0, file.getName().length() - 5));
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public static String safeName(String input) throws IOException {
        if (input == null) throw new IOException("蓝图名称不能为空");
        String value = input.trim();
        if (value.toLowerCase(Locale.ROOT).endsWith(".gtbp")) value = value.substring(0, value.length() - 5);
        if (value.isEmpty() || value.length() > 96 || value.equals(".") || value.equals("..")) {
            throw new IOException("蓝图名称长度无效");
        }
        if (!value.matches("[\\p{L}\\p{N}_ .()\\-]+")) throw new IOException("蓝图名称包含不允许的字符");
        return value;
    }

    private static final class LimitedInputStream extends FilterInputStream {

        private long remaining;

        private LimitedInputStream(InputStream input, long limit) {
            super(input);
            remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining == 0) return ensureEnd();
            int value = super.read();
            if (value >= 0) remaining--;
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining == 0) return ensureEnd();
            int allowed = (int) Math.min(length, remaining);
            int read = super.read(buffer, offset, allowed);
            if (read > 0) remaining -= read;
            return read;
        }

        private int ensureEnd() throws IOException {
            if (super.read() < 0) return -1;
            throw new IOException("蓝图解压后超过安全限制");
        }
    }
}
