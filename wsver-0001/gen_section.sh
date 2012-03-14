#!/bin/sh

# $1: markdown file
# $2: html template file
# $3: output html file

# Run markdown on the .md
markdown_py $1 -x toc > manual1.tmp

# Add a root element so it's well formed xml
echo "<div>" > top.tmp
echo "</div>" > end.tmp
cat top.tmp manual1.tmp end.tmp > manual2.tmp

# Run xsl transform on it to add class attributes and other tweaks needed
# for good presentation using Twitter Bootstrap
xsltproc addstyles.xsl manual2.tmp > manual3.tmp

# Insert the generated content in the actual HTML page
sed '/<!-- GENERATED CONTENT -->/ {
r manual3.tmp
d
}' $2 | cat > $3

rm manual*.tmp
rm top.tmp
rm end.tmp
