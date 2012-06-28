# epydoc -- Regression testing
#
# Copyright (C) 2005 Edward Loper
# Author: Edward Loper <edloper@loper.org>
# URL: <http://epydoc.sf.net>
#
# $Id: __init__.py 1502 2007-02-14 08:38:44Z edloper $

"""
Regression testing.
"""
__docformat__ = 'epytext en'

import unittest, doctest, epydoc, os, os.path, re, sys

def main():
    try:
        doctest.register_optionflag
    except:
        print ("\n"
            "The regression test suite requires a more recent version of\n"
            "doctest (e.g., the version that ships with Python 2.4 or 2.5).\n"
            "Please place a new version of doctest on your path before \n"
            "running the test suite.\n")
        return
                          
    
    PY24 = doctest.register_optionflag('PYTHON2.4')
    """Flag indicating that a doctest example requires Python 2.4+"""
    
    PY25 = doctest.register_optionflag('PYTHON2.5')
    """Flag indicating that a doctest example requires Python 2.5+"""
    
    class DocTestParser(doctest.DocTestParser):
        """
        Custom doctest parser that adds support for two new flags
        +PYTHON2.4 and +PYTHON2.5.
        """
        def parse(self, string, name='<string>'):
            pieces = doctest.DocTestParser.parse(self, string, name)
            for i, val in enumerate(pieces):
                if (isinstance(val, doctest.Example) and
                    ((val.options.get(PY24, False) and
                      sys.version[:2] < (2,4)) or
                     (val.options.get(PY25, False) and
                      sys.version[:2] < (2,5)))):
                    pieces[i] = doctest.Example('1', '1')
            return pieces

    # Turn on debugging.
    epydoc.DEBUG = True
    
    # Options for doctest:
    options = doctest.ELLIPSIS
    doctest.set_unittest_reportflags(doctest.REPORT_UDIFF)

    # Use a custom parser
    parser = DocTestParser()
    
    # Find all test cases.
    tests = []
    testdir = os.path.join(os.path.split(__file__)[0])
    if testdir == '': testdir = '.'
    for filename in os.listdir(testdir):
        if (filename.endswith('.doctest') and
            check_requirements(os.path.join(testdir, filename))):
            tests.append(doctest.DocFileSuite(filename, optionflags=options,
                                              parser=parser))
            
    # Run all test cases.
    unittest.TextTestRunner(verbosity=2).run(unittest.TestSuite(tests))

def check_requirements(filename):
    """
    Search for strings of the form::
    
        [Require: <module>]

    If any are found, then try importing the module named <module>.
    If the import fails, then return False.  If all required modules
    are found, return True.  (This includes the case where no
    requirements are listed.)
    """
    s = open(filename).read()
    for m in re.finditer('(?mi)^[ ]*\:RequireModule:(.*)$', s):
        module = m.group(1).strip()
        try:
            __import__(module)
        except ImportError:
            print ('Skipping %r (required module %r not found)' %
                   (os.path.split(filename)[-1], module))
            return False
    return True
            

if __name__=='__main__':
    main()
