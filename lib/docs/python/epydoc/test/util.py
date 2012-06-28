#
# epydoc -- Utility functions used by regression tests (*.doctest)
# Edward Loper
#
# Created [01/30/01 05:18 PM]
# $Id: html.py 1420 2007-01-28 14:19:30Z dvarrazzo $
#

"""
Utility functions used by the regression tests (C{*.doctest}).
"""
__docformat__ = 'epytext en'

import tempfile, re, os, os.path, textwrap, sys
from epydoc.docbuilder import build_doc, build_doc_index
from epydoc.docparser import parse_docs
from epydoc.docintrospecter import introspect_docs
from epydoc.apidoc import ClassDoc, RoutineDoc
from epydoc.markup import ParsedDocstring
from epydoc.docwriter.html import HTMLWriter

######################################################################
#{ Test Functions
######################################################################

def buildvaluedoc(s):
    """
    This test function takes a string containing the contents of a
    module.  It writes the string contents to a file, imports the file
    as a module, and uses build_doc to build documentation, and
    returns it as a C{ValueDoc} object.
    """
    tmp_dir = write_pystring_to_tmp_dir(s)
    val_doc = build_doc(os.path.join(tmp_dir, 'epydoc_test.py'))
    cleanup_tmp_dir(tmp_dir)
    return val_doc

def runbuilder(s, attribs='', build=None, exclude=''):
    """
    This test function takes a string containing the contents of a
    module.  It writes the string contents to a file, imports the file
    as a module, and uses build_doc to build documentation, and pretty
    prints the resulting ModuleDoc object.  The C{attribs} argument
    specifies which attributes of the C{APIDoc}s should be displayed.
    The C{build} argument gives the name of a variable in the module
    whose documentation should be built, instead of bilding docs for
    the whole module.
    """
    # Write it to a temp file.
    tmp_dir = write_pystring_to_tmp_dir(s)
    # Build it.
    val_doc = build_doc(os.path.join(tmp_dir, 'epydoc_test.py'))
    if build: val_doc = val_doc.variables[build].value
    # Display it.
    if isinstance(val_doc, ClassDoc):
        for val in val_doc.variables.values():
            if isinstance(val.value, RoutineDoc):
                fun_to_plain(val.value)
    s = val_doc.pp(include=attribs.split(),exclude=exclude.split())
    s = re.sub(r"(filename = ).*", r"\1...", s)
    s = re.sub(r"(<module 'epydoc_test' from ).*", r'\1...', s)
    s = re.sub(r"(<function \w+ at )0x\w+>", r"\1...>", s)
    s = re.sub(r"(<\w+ object at )0x\w+>", r"\1...>", s)
    print s
    # Clean up.
    cleanup_tmp_dir(tmp_dir)

def runparser(s, attribs='', show=None, exclude=''):
    """
    This test function takes a string containing the contents of a
    module, and writes it to a file, uses `parse_docs` to parse it,
    and pretty prints the resulting ModuleDoc object.  The `attribs`
    argument specifies which attributes of the `APIDoc`s should be
    displayed.  The `show` argument, if specifies, gives the name of
    the object in the module that should be displayed (but the whole
    module will always be inspected; this just selects what to
    display).
    """
    # Write it to a temp file.
    tmp_dir = write_pystring_to_tmp_dir(s)
    # Parse it.
    val_doc = parse_docs(os.path.join(tmp_dir, 'epydoc_test.py'))
    if show is not None:
        for name in show.split('.'):
            if isinstance(val_doc, ClassDoc):
                val_doc = val_doc.local_variables[name].value
            else:
                val_doc = val_doc.variables[name].value
    # Display it.
    s = val_doc.pp(include=attribs.split(), exclude=exclude.split())
    s = re.sub(r"filename = .*", "filename = ...", s)
    print s
    # Clean up.
    cleanup_tmp_dir(tmp_dir)

def runintrospecter(s, attribs='', introspect=None, exclude=''):
    """
    This test function takes a string containing the contents of a
    module.  It writes the string contents to a file, imports the file
    as a module, and uses C{introspect_docs} to introspect it, and
    pretty prints the resulting ModuleDoc object.  The C{attribs}
    argument specifies which attributes of the C{APIDoc}s should be
    displayed.  The C{introspect} argument gives the name of a variable
    in the module whose value should be introspected, instead of
    introspecting the whole module.
    """
    # Write it to a temp file.
    tmp_dir = write_pystring_to_tmp_dir(s)
    # Import it.
    sys.path.insert(0, tmp_dir)
    if introspect is None:
        import epydoc_test as val
    else:
        exec("from epydoc_test import %s as val" % introspect)
    del sys.path[0]
    # Introspect it.
    val_doc = introspect_docs(val)
    # Display it.
    s = val_doc.pp(include=attribs.split(),exclude=exclude.split())
    s = re.sub(r"(filename = ).*", r"\1...", s)
    s = re.sub(r"(<module 'epydoc_test' from ).*", r'\1...', s)
    s = re.sub(r"(<function \w+ at )0x\w+>", r"\1...>", s)
    s = re.sub(r"(<\w+ object at )0x\w+>", r"\1...>", s)
    print s
    # Clean up.
    cleanup_tmp_dir(tmp_dir)

def print_warnings():
    """
    Register a logger that will print warnings & errors.
    """
    from epydoc import log
    del log._loggers[:]
    log.register_logger(log.SimpleLogger(log.DOCSTRING_WARNING))

def testencoding(s, introspect=True, parse=True, debug=False):
    """
    An end-to-end test for unicode encodings.  This function takes a
    given string, writes it to a python file, and processes that
    file's documentation.  It then generates HTML output from the
    documentation, extracts all docstrings from the generated HTML
    output, and displays them.  (In order to extract & display all
    docstrings, it monkey-patches the HMTLwriter.docstring_to_html()
    method.)"""
    # Monkey-patch docstring_to_html
    original_docstring_to_html = HTMLWriter.docstring_to_html
    HTMLWriter.docstring_to_html = print_docstring_as_html
    
    # Write s to a temporary file.
    tmp_dir = tempfile.mkdtemp()
    path = os.path.join(tmp_dir, 'enc_test.py')
    out = open(path, 'w')
    out.write(textwrap.dedent(s))
    out.close()
    # Build docs for it
    docindex = build_doc_index([path], introspect, parse)
    if docindex is None: return
    sys.modules.pop('enc_test', None)
    # Write html output.
    writer = HTMLWriter(docindex, mark_docstrings=True)
    writer.write(tmp_dir)
    for file in os.listdir(tmp_dir):
        os.unlink(os.path.join(tmp_dir,file))
    os.rmdir(tmp_dir)

    # Restore the HTMLWriter class to its original state.
    HTMLWriter.docstring_to_html = original_docstring_to_html

######################################################################
#{ Helper Functions
######################################################################

def write_pystring_to_tmp_dir(s):
    tmp_dir = tempfile.mkdtemp()
    out = open(os.path.join(tmp_dir, 'epydoc_test.py'), 'w')
    out.write(textwrap.dedent(s))
    out.close()
    return tmp_dir

def cleanup_tmp_dir(tmp_dir):
    os.unlink(os.path.join(tmp_dir, 'epydoc_test.py'))
    try: os.unlink(os.path.join(tmp_dir, 'epydoc_test.pyc'))
    except OSError: pass
    os.rmdir(tmp_dir)
    sys.modules.pop('epydoc_test', None)

def to_plain(docstring):
    """Conver a parsed docstring into plain text"""
    if isinstance(docstring, ParsedDocstring):
        docstring = docstring.to_plaintext(None)
    return docstring.rstrip()

def fun_to_plain(val_doc):
    """Convert parsed docstrings in text from a RoutineDoc"""
    for k, v in val_doc.arg_types.items():
        val_doc.arg_types[k] = to_plain(v)
    for i, (k, v) in enumerate(val_doc.arg_descrs):
        val_doc.arg_descrs[i] = (k, to_plain(v))

def print_docstring_as_html(self, parsed_docstring, *varargs, **kwargs):
    """
    Convert the given parsed_docstring to HTML and print it.  Ignore
    any other arguments.  This function is used by L{testencoding} to
    monkey-patch the HTMLWriter class's docstring_to_html() method.
    """
    s = parsed_docstring.to_html(None).strip()
    s = s.encode('ascii', 'xmlcharrefreplace')
    s = remove_surrogates(s)
    print s
    return ''

def remove_surrogates(s):
    """
    The following is a helper function, used to convert two-character
    surrogate sequences into single characters.  This is needed
    because some systems create surrogates but others don't.
    """
    pieces = re.split('(&#\d+;)', s)
    for i in range(3, len(pieces)-1, 2):
        if pieces[i-1] != '': continue
        high,low = int(pieces[i-2][2:-1]), int(pieces[i][2:-1])
        if 0xd800 <= high <= 0xdbff and 0xdc00 <= low <= 0xdfff:
            pieces[i-2] = '&#%d;' % (((high&0x3ff)<<10) +
                                     (low&0x3ff) + 0x10000)
            pieces[i] = ''
    return ''.join(pieces)
