
package de.upb.maven.ecosystem;

import com.google.common.collect.Sets;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PomFileUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(PomFileUtil.class);
    private static final Set<String> ARCHIVE_TYPES = Sets.newHashSet(new String[]{"zip", "war", "jar"});

    public PomFileUtil() {
    }

    @Nullable
    public static MavenProject readPom(Path foundPomFile) {
        try {
            Path path = foundPomFile;
            URI uri = foundPomFile.toUri();
            if (isArchiveFile(uri)) {
                path = acquireZipFs(foundPomFile, uri);
            }

            return readPom(Files.newInputStream(path));
        } catch (IOException var3) {
            LOGGER.error("Failed to read POM ", var3);
            return null;
        }
    }

    public static synchronized Path acquireZipFs(Path path, URI uri) throws IOException {
        String[] zipFile = uri.toString().split("!");

        try {
            FileSystem fs = path.getFileSystem().provider().getFileSystem(uri);
            path = fs.getPath("/").resolve(zipFile[1]);
        } catch (Exception var6) {
            Map<String, String> env = new HashMap();
            env.put("create", "false");
            Path root = FileSystems.newFileSystem(URI.create(zipFile[0]), env).getPath("/");
            path = root.resolve(zipFile[1]);
        }

        return path;
    }

    public static boolean isArchiveFile(URI uri) {
        return ARCHIVE_TYPES.contains(uri.getScheme());
    }

    @Nullable
    public static MavenProject readPom(InputStream foundPomFileStream) {
        try {
            MavenXpp3Reader mavenreader = new MavenXpp3Reader();
            Model model = mavenreader.read(foundPomFileStream);
            return new MavenProject(model);
        } catch (XmlPullParserException | IOException var3) {
            LOGGER.error("Failed to read POM ", var3);
            return null;
        }
    }
}
