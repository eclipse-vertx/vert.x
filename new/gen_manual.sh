#!/bin/sh

redcloth manual.txt > manual.gen

sed '/<!-- USER MANUAL -->/ {
r manual.gen
d
}' web/usermanual_templ.html | cat > web/usermanual.html