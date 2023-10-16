package de.upb.maven.ecosystem.persistence.fingerprint.model.dao;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.base.Objects;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"name", "url", "distribution", "comments", "licenseText"})
@Entity
@Table(name = "licenses")
public class License {

  @Id @GeneratedValue private long id;

  @JsonProperty("name")
  private String name;

  @JsonProperty("url")
  private String url;

  @JsonProperty("distribution")
  private String distribution;

  @JsonProperty("comments")
  private String comments;

  @JsonProperty("licenseText")
  private String licenseText;

  /**
   * Shorten String to fit into postgres db
   *
   * @param text
   * @return
   */
  private static String shortenString(String text) {
    if (text == null) {
      return "";
    }

    final String substring = text.substring(0, Math.min(text.length(), 253));
    String s = substring.replaceAll("\u0000", "");
    s = s.replace("\\x00", " ");
    s = s.replace("E'", "'");
    return s;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = shortenString(name);
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {

    this.url = shortenString(url);
  }

  public String getDistribution() {
    return distribution;
  }

  public void setDistribution(String distribution) {

    this.distribution = shortenString(distribution);
  }

  public String getComments() {
    return comments;
  }

  public void setComments(String comments) {

    this.comments = shortenString(comments);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    License that = (License) o;

    return Objects.equal(this.name, that.name)
        && Objects.equal(this.url, that.url)
        && Objects.equal(this.distribution, that.distribution)
        && Objects.equal(this.comments, that.comments)
        && Objects.equal(this.licenseText, that.licenseText);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name, url, distribution, comments, licenseText);
  }
}
