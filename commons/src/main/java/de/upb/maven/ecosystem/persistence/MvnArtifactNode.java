package de.upb.maven.ecosystem.persistence;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MvnArtifactNode {

    // is auto generated by neo?
    private Long id;

    private String group;
    private String artifact;
    private String version;

    private String classifier;

    private String type = "jar";

    private Map<String, String> properties = new HashMap<>();

    //relationship type=PARENT
    private MvnArtifactNode parent;

    // must be list to be ordered, in the mvn resolution process the order of dependencies matters for resolving
    private List<DependencyRelation> dependencies = new ArrayList<>();

    private List<DependencyRelation> dependencyManagement = new ArrayList<>();

}
