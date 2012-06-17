import pydoc
import os
import re
import shutil

class CustomHTMLDoc(pydoc.HTMLDoc):
    def docmodule(self, object, name=None, mod=None, *ignored):
        res = pydoc.HTMLDoc.docmodule(self, object, name, mod, *ignored)
        res = re.sub('<a href="file:.*?<\/a>', "", res) # remove the link to local files
        res = res.replace('__builtin__.html#object','http://pydoc.org/2.5.1/__builtin__.html#object') # fix the built-in refs        
        return res.replace('<a href=".">index</a>', '<a href="index.html">index</a>') # link to index.html

    def write_index(self):
        heading = self.heading(
        '<big><big><strong>vert.x Python API</strong></big></big>',
        '#ffffff', '#7799ee')
        index = self.index(".", {})
        contents = heading + index + '''<p align=right>
        <font color="#909090" face="helvetica, arial"><strong>
        pydoc</strong> by Ka-Ping Yee &lt;ping@lfw.org&gt;</font>'''
        
        page = self.page("vert.x: Index of Modules", contents)
        page = page.replace('<strong>.</strong>', '<strong>API</strong>')
        file = open(APIDOC_DIR+'index.html', 'w')
        file.write(page)
        file.close()
        print 'wrote', APIDOC_DIR+'index.html'

def custom_writedoc(thing, forceload=0):
    """Write HTML documentation to specific directory"""
    try:
        object, name = pydoc.resolve(thing, forceload)
        page = pydoc.html.page(pydoc.describe(object), pydoc.html.document(object, name))
        file = open(APIDOC_DIR + name + '.html', 'w')
        file.write(page)
        file.close()
        print 'wrote', APIDOC_DIR + name + '.html'
    except (ImportError, pydoc.ErrorDuringImport), value:
        print value

APIDOC_DIR = os.getcwd()+"/target/docs/python/api/"
shutil.rmtree("target/docs/python")
os.makedirs(APIDOC_DIR)

os.chdir("src/main/python")
pydoc.html = CustomHTMLDoc()
pydoc.writedoc = custom_writedoc
pydoc.writedocs("./")
pydoc.html.write_index()