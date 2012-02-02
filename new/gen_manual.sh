#!/bin/sh

# Run markdown on the .md
perl Markdown.pl manual.md >> manual1.tmp

# Add a root element so it's well formed xml
echo "<div>" > top.tmp
echo "</div>" > end.tmp
cat top.tmp manual1.tmp end.tmp > manual2.tmp

# Run xsl transform on it to add class attributes and other tweaks needed
# for good presentation using Twitter Bootstrap
xsltproc addstyles.xsl manual2.tmp > manual3.tmp

# Insert the generated content in the actual HTML page
sed '/<!-- USER MANUAL -->/ {
r manual3.tmp
d
}' web/usermanual_templ.html | cat > web/usermanual.html

rm manual*.tmp
rm top.tmp
rm end.tmp
