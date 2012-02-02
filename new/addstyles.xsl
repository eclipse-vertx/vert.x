<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

<xsl:template match="/">
  <xsl:copy>
    <xsl:apply-templates />
  </xsl:copy>
</xsl:template>

<!-- Add a class attribute to pre tags for pretty printing code -->
<xsl:template match="pre">
  <xsl:copy>
    <xsl:attribute name="class">prettyprint</xsl:attribute>
    <xsl:apply-templates />
  </xsl:copy>
</xsl:template>

<!-- We need to remove the code tags since the Twitter bootstrap CSS doesn't
like them for code blocks -->
<xsl:template match="code">
  <xsl:apply-templates />
</xsl:template>

</xsl:stylesheet>
