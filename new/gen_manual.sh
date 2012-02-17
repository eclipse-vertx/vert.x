#!/bin/sh

./gen_section.sh manual.md manual_templ.html web/manual.html
./gen_section.sh bus_mods_manual.md bus_mods_manual_templ.html web/bus_mods_manual.html
./gen_section.sh core_manual_js.md core_manual_js_templ.html web/core_manual_js.html
./gen_section.sh core_manual_ruby.md core_manual_ruby_templ.html web/core_manual_ruby.html
./gen_section.sh core_manual_java.md core_manual_java_templ.html web/core_manual_java.html
./gen_section.sh install.md install_manual_templ.html web/install.html


