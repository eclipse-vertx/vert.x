#!/bin/sh

./gen_section.sh docs_md/manual.md manual_templ.html manual.html
./gen_section.sh docs_md/mods_manual.md mods_manual_templ.html mods_manual.html
./gen_section.sh docs_md/core_manual_js.md core_manual_js_templ.html core_manual_js.html
./gen_section.sh docs_md/core_manual_ruby.md core_manual_ruby_templ.html core_manual_ruby.html
./gen_section.sh docs_md/core_manual_python.md core_manual_python_templ.html core_manual_python.html
./gen_section.sh docs_md/core_manual_java.md core_manual_java_templ.html core_manual_java.html
./gen_section.sh docs_md/core_manual_groovy.md core_manual_groovy_templ.html core_manual_groovy.html
./gen_section.sh docs_md/install.md install_manual_templ.html install.html
./gen_section.sh docs_md/js_web_tutorial.md js_web_tutorial_templ.html js_web_tutorial.html
./gen_section.sh docs_md/ruby_web_tutorial.md ruby_web_tutorial_templ.html ruby_web_tutorial.html
./gen_section.sh docs_md/python_web_tutorial.md python_web_tutorial_templ.html python_web_tutorial.html
./gen_section.sh docs_md/groovy_web_tutorial.md groovy_web_tutorial_templ.html groovy_web_tutorial.html
