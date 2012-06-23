import pydoc
import os
import re
import shutil
import sys
import __builtin__

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

    def classlink(self, object, modname):
        """Make a link for a class."""
        name, module = object.__name__, sys.modules.get(object.__module__)
        from java.lang import Object
        if (object is Object or 
            Object in object.__bases__ or
            object is __builtin__.object):
            return pydoc.classname(object, modname)
        elif hasattr(module, name) and getattr(module, name) is object:
            return '<a href="%s.html#%s">%s</a>' % (
                module.__name__, name, pydoc.classname(object, modname))
        return pydoc.classname(object, modname)

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


def custom_classify_class_attrs(cls):
    """Override inspect.classify_class_attrs to catch errors on java objects"""
    mro = pydoc.inspect.getmro(cls)
    names = dir(cls)
    result = []
    for name in names:
        try:
            # Get the object associated with the name.
            # Getting an obj from the __dict__ sometimes reveals more than
            # using getattr.  Static and class methods are dramatic examples.
            if name in cls.__dict__:
                obj = cls.__dict__[name]
            else:
                obj = getattr(cls, name)

            # Figure out where it was defined.
            homecls = getattr(obj, "__objclass__", None)
            if homecls is None:
                # search the dicts.
                for base in mro:
                    if name in base.__dict__:
                        homecls = base
                        break

            # Get the object again, in order to get it from the defining
            # __dict__ instead of via getattr (if possible).
            if homecls is not None and name in homecls.__dict__:
                obj = homecls.__dict__[name]

            # Also get the object via getattr.
            obj_via_getattr = getattr(cls, name)

            # Classify the object.
            if isinstance(obj, staticmethod):
                kind = "static method"
            elif isinstance(obj, classmethod):
                kind = "class method"
            elif isinstance(obj, property):
                kind = "property"
            elif (pydoc.inspect.ismethod(obj_via_getattr) or
                  pydoc.inspect.ismethoddescriptor(obj_via_getattr)):
                kind = "method"
            else:
                kind = "data"

            result.append((name, kind, homecls, obj))
        except:
            pass
    return result

def custom_getclasstree(classes, unique=0):
    children = {}
    roots = []
    for c in classes:        
        if c.__bases__:
            for parent in c.__bases__:
                if not parent in children:
                    children[parent] = []
                children[parent].append(c)
                if unique and parent in classes: break
        elif c not in roots:
            roots.append(c)
    extrachildren = {}
    for parent in children:
        if hasattr(parent, "name") and parent.name.startswith("org.python.proxies"):
            base = parent.__bases__[0]
            if base not in roots:
                roots.append(base)
            if not base in extrachildren:
                extrachildren[base] = []    
            extrachildren[base].append(parent)
        elif parent not in classes : 
            roots.append(parent)
    children.update(extrachildren)
    return pydoc.inspect.walktree(roots, children, None)

# Remove and recreate the output docs directory
APIDOC_DIR = os.getcwd()+"/target/docs/python/api/"
try:
    shutil.rmtree("target/docs/python")
except: pass
os.makedirs(APIDOC_DIR)
os.chdir("src/main/python")

# Replace some of the pydoc methods to all them to work with java inheritance
pydoc.html = CustomHTMLDoc()
pydoc.inspect.classify_class_attrs = custom_classify_class_attrs
pydoc.inspect.getclasstree = custom_getclasstree
pydoc.writedoc = custom_writedoc
pydoc.writedocs("./")
pydoc.html.write_index()