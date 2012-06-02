<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

<xsl:output omit-xml-declaration="yes" indent="yes"/>

<!-- Markdown doesn't let you insert a line break after a header :( so
we do this in the stylesheet -->
<xsl:template match="h1|h2|h3|h4|h5|h6">
  <xsl:copy>
    <xsl:copy-of select="@*"/>
    <xsl:apply-templates/>
  </xsl:copy>
  <br/>
</xsl:template>

<!-- remove any code elements nested in pre elements since Twitter Bootstrap
requires just pre elements. Also add a prettyprint class to the pre -->
<xsl:template match="pre/code">
  <xsl:attribute name="class">prettyprint</xsl:attribute>
  <xsl:apply-templates/>
</xsl:template>

<!-- catch all -->
<xsl:template match="*">
  <xsl:copy>
    <xsl:copy-of select="@*"/>
    <xsl:apply-templates/>
  </xsl:copy>
</xsl:template>



</xsl:stylesheet>
