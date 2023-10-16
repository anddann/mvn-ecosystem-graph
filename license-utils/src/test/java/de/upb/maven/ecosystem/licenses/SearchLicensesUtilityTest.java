package de.upb.maven.ecosystem.licenses;

import static org.junit.Assert.*;

import de.upb.maven.ecosystem.PomFileUtil;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.BiConsumer;
import org.apache.maven.model.License;
import org.apache.maven.model.Organization;
import org.apache.maven.project.MavenProject;
import org.cyclonedx.model.Component;
import org.junit.Test;

public class SearchLicensesUtilityTest {

  @Test
  public void readPomPlainFile() {
    Path resourceDirectory = Paths.get("src", "test", "resources", "pom.xml");
    final MavenProject mavenProject = PomFileUtil.readPom(resourceDirectory);
    assertNotNull(mavenProject);
    assertEquals(1, mavenProject.getLicenses().size());
    License license0 = mavenProject.getLicenses().get(0);
    assertEquals("UPB License", license0.getName());
    assertEquals("https://www.gnu.org/licenses/lgpl-2.1.txt", license0.getUrl());
    assertEquals("testDistribution", license0.getDistribution());

    Organization organization = mavenProject.getOrganization();
    assertNotNull(organization);
    assertEquals("UPB Inc.", organization.getName());
    assertEquals("https://upb.de", organization.getUrl());
  }

  @Test
  public void searchLicenceFiles() {
    Path resourceDirectory = Paths.get("src", "test", "resources", "licence_test.jar");
    HashSet<MavenProject> foundProjects = new HashSet<>();
    BiConsumer<MavenProject, Component> addInfoToComponent = (mp, comp) -> foundProjects.add(mp);

    SearchLicensesUtility.getInstance()
        .searchLicenseFile(resourceDirectory, addInfoToComponent, null);
    assertTrue(!foundProjects.isEmpty());
    Collection<License> foundLincenses = new HashSet<>();

    foundProjects.stream().forEach(x -> foundLincenses.addAll(x.getLicenses()));
    assertEquals(19, foundLincenses.size());
    // TODO: re-check expectations
    HashSet<String> expectedLicenses = new HashSet<>();
    expectedLicenses.add("Apache License 2.0");
    expectedLicenses.add("MIT License");
    expectedLicenses.add("DOC License");
    expectedLicenses.add("GNU Lesser General Public License v3.0 only");
    expectedLicenses.add("Eclipse Public License 1.0");

    for (License license : foundLincenses) {

      expectedLicenses.remove(license.getName());
    }

    assertEquals(0, expectedLicenses.size());
  }
}
