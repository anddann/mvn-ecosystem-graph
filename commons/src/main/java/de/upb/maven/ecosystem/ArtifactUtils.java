package de.upb.maven.ecosystem;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.model.MvnArtifactNode;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import org.apache.commons.lang3.StringUtils;

/** @author adann */
public class ArtifactUtils {
  public static URL constructURL(CustomArtifactInfo info) throws MalformedURLException {
    // CAUTION: the url is not the right download url. The download url ist replaced
    ArrayList<String> res = Lists.newArrayList();

    String classifier = "";

    if (StringUtils.isNotBlank(info.getClassifier())
        && !StringUtils.equals("null", info.getClassifier())) {
      classifier = "-" + info.getClassifier();
    }

    String repoURL = info.getRepoURL();
    if (StringUtils.isBlank(repoURL)) {
      throw new IllegalArgumentException("Repo URL is blank");
    }

    if (repoURL.endsWith("/")) {
      repoURL = repoURL.substring(0, repoURL.length() - 1);
    }
    res.add(repoURL);
    String gId = info.getGroupId().replace(".", "/");
    res.add(gId);
    String aId = info.getArtifactId();
    res.add(aId);
    String vId = info.getArtifactVersion();
    res.add(vId);
    res.add(aId + "-" + vId + classifier + "." + info.getFileExtension());
    return new URL(Joiner.on("/").join(res));
  }

  public static URL constructURL(MvnArtifactNode info) throws MalformedURLException {
    CustomArtifactInfo customArtifactInfo = new CustomArtifactInfo();
    customArtifactInfo.setGroupId(info.getGroup());
    customArtifactInfo.setArtifactId(info.getArtifact());
    customArtifactInfo.setArtifactVersion(info.getVersion());
    customArtifactInfo.setClassifier(info.getClassifier());
    customArtifactInfo.setPackaging(info.getPackaging());
    customArtifactInfo.setRepoURL(info.getRepoURL());
    customArtifactInfo.setFileExtension("pom");
    return constructURL(customArtifactInfo);
  }

  public static boolean ignoredArtifactType(CustomArtifactInfo ai) {
    //  we should only handle artifacts with classifier =null for dependency resolving
    // ignore src, test JARs
    return StringUtils.isNotBlank(ai.getClassifier());
  }
}
