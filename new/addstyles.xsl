<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

<xsl:output omit-xml-declaration="yes" indent="yes"/>

<xsl:template match="*">
  <xsl:copy>
    <xsl:copy-of select="@*"/>
    <xsl:apply-templates/>
  </xsl:copy>
</xsl:template>

<!-- remove any code elements nested in pre elements since Twitter Bootstrap
requires just pre elements. Also add a prettyprint class to the pre -->
<xsl:template match="pre/code">
    <xsl:attribute name="class">prettyprint</xsl:attribute>
    <xsl:apply-templates/>
</xsl:template>


</xsl:stylesheet>
