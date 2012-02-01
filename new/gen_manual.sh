#!/bin/sh

rm manual.gen*

perl Markdown.pl manual.md >> manual.gen
echo "<div>" > top.txt
echo "</div>" > end.txt
cat top.txt manual.gen end.txt > manual.gen2
xsltproc addstyles.xslt manual.gen2 > manual.gen3

sed '/<!-- USER MANUAL -->/ {
r manual.gen3
d
}' web/usermanual_templ.html | cat > web/usermanual.html