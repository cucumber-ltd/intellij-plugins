package com.jetbrains.lang.dart.util;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class DotPackagesFileUtil {

  public static final String DOT_PACKAGES = ".packages";

  private static final Key<Pair<Long, Map<String, String>>> MOD_STAMP_TO_PACKAGES_MAP = Key.create("MOD_STAMP_TO_PACKAGES_MAP");

  @Nullable
  public static Map<String, String> getPackagesMap(@NotNull final VirtualFile dotPackagesFile) {
    Pair<Long, Map<String, String>> data = dotPackagesFile.getUserData(MOD_STAMP_TO_PACKAGES_MAP);

    final Long currentTimestamp = dotPackagesFile.getModificationCount();
    final Long cachedTimestamp = data == null ? null : data.first;

    if (cachedTimestamp == null || !cachedTimestamp.equals(currentTimestamp)) {
      data = null;
      dotPackagesFile.putUserData(MOD_STAMP_TO_PACKAGES_MAP, null);
      final Map<String, String> packagesMap = loadPackagesMap(dotPackagesFile);

      if (packagesMap != null) {
        data = Pair.create(currentTimestamp, packagesMap);
        dotPackagesFile.putUserData(MOD_STAMP_TO_PACKAGES_MAP, data);
      }
    }

    return data == null ? null : data.second;
  }

  @Nullable
  private static Map<String, String> loadPackagesMap(@NotNull final VirtualFile dotPackagesFile) {
    try {
      final List<String> lines = FileUtil.loadLines(dotPackagesFile.getPath(), "UTF-8");
      final Map<String, String> result = new THashMap<String, String>();

      for (String line : lines) {
        if (line.trim().isEmpty() || line.startsWith("#")) continue;

        final int colonIndex = line.indexOf(':');
        if (colonIndex > 0 && colonIndex < line.length() - 1) {
          final String packageName = line.substring(0, colonIndex).trim();
          final String packageUri = getAbsolutePackageRootPath(dotPackagesFile.getParent(), line.substring(colonIndex + 1).trim());
          if (!packageName.isEmpty() && packageUri != null) {
            result.put(packageName, packageUri);
          }
        }
      }

      return result;
    }
    catch (IOException e) {
      return null;
    }
  }

  @Nullable
  private static String getAbsolutePackageRootPath(@NotNull final VirtualFile baseDir, @NotNull final String uri) {
    if (uri.startsWith("file:/")) {
      final String pathAfterSlashes = StringUtil.trimEnd(StringUtil.trimLeading(StringUtil.trimStart(uri, "file:/"), '/'), "/");
      if (SystemInfo.isWindows) {
        if (pathAfterSlashes.length() > 2 && Character.isLetter(pathAfterSlashes.charAt(0)) && ':' == pathAfterSlashes.charAt(1)) {
          return pathAfterSlashes;
        }
      }
      else {
        return "/" + pathAfterSlashes;
      }
    }
    else {
      return FileUtil.toCanonicalPath(baseDir.getPath() + "/" + uri);
    }

    return null;
  }
}
