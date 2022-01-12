package de.upb.maven.ecosystem.persistence;

// the relationship
public class DependencyRelation {


    private DependencyScope scope;
    private boolean optional;

    private MvnArtifactNode dependency;

    private String type = "jar";

    // in the mvn resolution process the order of dependencies matters for resolving. Thus, we store its position in the pom file, starting from 0
    private int position;

}
