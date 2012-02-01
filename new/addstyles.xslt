<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

<xsl:template match="*">
  <xsl:copy>
    <xsl:apply-templates />
  </xsl:copy>
</xsl:template>

<xsl:template match="pre">
  <xsl:copy>
    <xsl:attribute name="class">prettyprint</xsl:attribute>
    <xsl:apply-templates />
  </xsl:copy>
</xsl:template>

</xsl:stylesheet>