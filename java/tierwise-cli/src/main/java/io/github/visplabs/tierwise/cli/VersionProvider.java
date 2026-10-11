package io.github.visplabs.tierwise.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import picocli.CommandLine.IVersionProvider;

/** Supplies the text of {@code --version}: the version of the build, from the filtered resource. */
final class VersionProvider implements IVersionProvider {

  @Override
  public String[] getVersion() {
    return new String[] {"tierwise " + version()};
  }

  /** Returns the version of this build, or {@code unknown} when the resource is not filtered. */
  static String version() {
    Properties properties = new Properties();
    try (InputStream in = VersionProvider.class.getResourceAsStream("version.properties")) {
      if (in != null) {
        properties.load(in);
      }
    } catch (IOException e) {
      return "unknown";
    }
    String version = properties.getProperty("version", "unknown");
    return version.startsWith("${") ? "unknown" : version;
  }
}
