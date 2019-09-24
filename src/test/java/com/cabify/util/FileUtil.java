package com.cabify.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileUtil {

	public static String loadFile(String path) {
		try {
			String base = "src/test/resources/";
			return Files.readString(Paths.get(base, path));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
