#
# epydoc.py: epydoc LaTeX output generator
# Edward Loper
#
# Created [01/30/01 05:18 PM]
# $Id: latex.py 1809 2008-03-05 18:40:49Z edloper $
#

"""
The LaTeX output generator for epydoc.  The main interface provided by
this module is the L{LatexWriter} class.

@todo: Inheritance=listed
"""
__docformat__ = 'epytext en'

import os.path, sys, time, re, textwrap, codecs

from epydoc.apidoc import *
from epydoc.compat import *
import epydoc
from epydoc import log
from epydoc import markup
from epydoc.util import plaintext_to_latex
import epydoc.markup
from epydoc.docwriter.dotgraph import *
from epydoc.docwriter.latex_sty import STYLESHEETS

class LatexWriter:
    #: Expects (options, epydoc_sty_package)
    PREAMBLE = [
        "\\documentclass{article}",
        "\\usepackage[%s]{%s}",
        ]

    SECTIONS = ['\\part{%s}', '\\chapter{%s}', '\\section{%s}',
                '\\subsection{%s}', '\\subsubsection{%s}',
                '\\textbf{%s}']

    STAR_SECTIONS = ['\\part*{%s}', '\\chapter*{%s}', '\\section*{%s}',
                     '\\subsection*{%s}', '\\subsubsection*{%s}',
                     '\\textbf{%s}']

    def __init__(self, docindex, **kwargs):
        self.docindex = docindex
        # Process keyword arguments
        self._show_private = kwargs.get('show_private', 0)
        self._prj_name = kwargs.get('prj_name', None)
        self._show_crossrefs = kwargs.get('crossref', 1)
        self._index = kwargs.get('index', 1)
        self._hyperlink = kwargs.get('hyperlink', True)
        self._list_classes_separately=kwargs.get('list_classes_separately',0)
        self._inheritance = kwargs.get('inheritance', 'listed')
        self._exclude = kwargs.get('exclude', 1)
        self._list_submodules = kwargs.get('list_submodules', 1)
        self._sty = kwargs.get('sty')
        self._top_section = 2
        self._index_functions = 1
        self._hyperref = 1
        self._show_submodule_list = kwargs.get('show_submodule_list', True)
        self._graph_types = kwargs.get('graphs', ()) or ()
        """Graphs that we should include in our output."""

        #: The Python representation of the encoding.
        #: Update L{latex_encodings} in case of mismatch between it and
        #: the C{inputenc} LaTeX package.
        self._encoding = kwargs.get('encoding', 'utf-8')

        self.valdocs = valdocs = sorted(docindex.reachable_valdocs(
            imports=False, packages=False, bases=False, submodules=False, 
            subclasses=False, private=self._show_private))
        self._num_files = self.num_files()
        # For use with select_variables():
        if self._show_private: self._public_filter = None
        else: self._public_filter = True

        self.class_list = [d for d in valdocs if isinstance(d, ClassDoc)]
        """The list of L{ClassDoc}s for the documented classes."""
        self.class_set = set(self.class_list)
        """The set of L{ClassDoc}s for the documented classes."""
        self.module_list = [d for d in valdocs if isinstance(d, ModuleDoc)]
        """The list of L{ModuleDoc}s for the documented modules."""
        self.module_set = set(self.module_list)
        """The set of L{ModuleDoc}s for the documented modules."""
        
    def write(self, directory=None):
        """
        Write the API documentation for the entire project to the
        given directory.

        @type directory: C{string}
        @param directory: The directory to which output should be
            written.  If no directory is specified, output will be
            written to the current directory.  If the directory does
            not exist, it will be created.
        @rtype: C{None}
        @raise OSError: If C{directory} cannot be created,
        @raise OSError: If any file cannot be created or written to.
        """
        # For progress reporting:
        self._files_written = 0.
        
        # Set the default values for ValueDoc formatted representations.
        orig_valdoc_defaults = (ValueDoc.SUMMARY_REPR_LINELEN,
                                ValueDoc.REPR_LINELEN,
                                ValueDoc.REPR_MAXLINES)
        ValueDoc.SUMMARY_REPR_LINELEN = 60
        ValueDoc.REPR_LINELEN = 52
        ValueDoc.REPR_MAXLINES = 5

        # Create destination directories, if necessary
        if not directory: directory = os.curdir
        self._mkdir(directory)
        self._directory = directory

        # Write the style file.
        self._write_sty(directory, self._sty)
        
        # Write the top-level file.
        self._write(self.write_topfile, directory, 'api.tex')

        # Write the module & class files.
        for val_doc in self.valdocs:
            if isinstance(val_doc, ModuleDoc):
                filename = '%s-module.tex' % val_doc.canonical_name
                self._write(self.write_module, directory, filename, val_doc)
            elif isinstance(val_doc, ClassDoc):
                filename = '%s-class.tex' % val_doc.canonical_name
                self._write(self.write_class, directory, filename, val_doc)

        # Restore defaults that we changed.
        (ValueDoc.SUMMARY_REPR_LINELEN, ValueDoc.REPR_LINELEN,
         ValueDoc.REPR_MAXLINES) = orig_valdoc_defaults

    def _write_sty(self, directory, stylesheet):
        """
        Copy the requested LaTeX stylesheet to the target directory.
        The stylesheet can be specified as a name (i.e., a key from
        the STYLESHEETS directory); a filename; or None for the default
        stylesheet.  If any stylesheet *other* than the default
        stylesheet is selected, then the default stylesheet will be
        copied to 'epydoc-default.sty', which makes it possible to
        reference it via \RequirePackage.
        """
        # Write all the standard style files
        for (name, sty) in STYLESHEETS.items():
            out = open(os.path.join(directory, 'epydoc-%s.sty' % name), 'wb')
            out.write(sty)
            out.close()

        # Default: use the 'epydoc-default' style.
        if stylesheet is None:
            self._epydoc_sty_package = 'epydoc-default'

        # Stylesheet name: use the indicated style.
        elif stylesheet in STYLESHEETS:
            self._epydoc_sty_package = 'epydoc-%s' % stylesheet

        # Custom user stylesheet: copy the style to epydoc-custom.
        elif os.path.exists(stylesheet):
            try: sty = open(stylesheet, 'rb').read()
            except: raise IOError("Can't open LaTeX style file: %r" %
                                  stylesheet)
            out = open(os.path.join(directory, 'epydoc-custom.sty'), 'wb')
            out.write(sty)
            out.close()
            self._epydoc_sty_package = 'epydoc-custom'

        else:
            raise IOError("Can't find LaTeX style file: %r" % stylesheet)
        
    def _write(self, write_func, directory, filename, *args):
        # Display our progress.
        self._files_written += 1
        log.progress(self._files_written/self._num_files, filename)
        
        path = os.path.join(directory, filename)
        if self._encoding == 'utf-8':
            f = codecs.open(path, 'w', 'utf-8')
            write_func(f.write, *args)
            f.close()
        else:
            result = []
            write_func(result.append, *args)
            s = u''.join(result)
            try:
                s = s.encode(self._encoding)
            except UnicodeError:
                log.error("Output could not be represented with the "
                          "given encoding (%r).  Unencodable characters "
                          "will be displayed as '?'.  It is recommended "
                          "that you use a different output encoding (utf-8, "
                          "if it's supported by latex on your system)."
                          % self._encoding)
                s = s.encode(self._encoding, 'replace')
            f = open(path, 'w')
            f.write(s)
            f.close()

    def num_files(self):
        """
        @return: The number of files that this C{LatexFormatter} will
            generate.
        @rtype: C{int}
        """
        return 1 + len([doc for doc in self.valdocs
                        if isinstance(doc, (ClassDoc, ModuleDoc))])
        
    def _mkdir(self, directory):
        """
        If the given directory does not exist, then attempt to create it.
        @rtype: C{None}
        """
        if not os.path.isdir(directory):
            if os.path.exists(directory):
                raise OSError('%r is not a directory' % directory)
            os.mkdir(directory)
            
    #////////////////////////////////////////////////////////////
    #{ Main Doc File
    #////////////////////////////////////////////////////////////

    def write_topfile(self, out):
        self.write_header(out, 'Include File')
        self.write_preamble(out)
        out('\n\\begin{document}\n\n')
        out(self.start_of('Header'))

        # Write the title.
        out(self.start_of('Title'))
        out('\\title{%s}\n' % plaintext_to_latex(
            self._prj_name or 'API Documentation', 1))
        out('\\author{API Documentation}\n')
        out('\\maketitle\n')

        # Add a table of contents.
        out(self.start_of('Table of Contents'))
        out('\\addtolength{\\parskip}{-1ex}\n')
        out('\\tableofcontents\n')
        out('\\addtolength{\\parskip}{1ex}\n')

        # Include documentation files.
        out(self.start_of('Includes'))
        for val_doc in self.valdocs:
            if isinstance(val_doc, ModuleDoc):
                out('\\include{%s-module}\n' % val_doc.canonical_name)

        # If we're listing classes separately, put them after all the
        # modules.
        if self._list_classes_separately:
            for val_doc in self.valdocs:
                if isinstance(val_doc, ClassDoc):
                    out('\\include{%s-class}\n' % val_doc.canonical_name)

        # Add the index, if requested.
        if self._index:
            out(self.start_of('Index'))
            out('\\printindex\n\n')

        # Add the footer.
        out(self.start_of('Footer'))
        out('\\end{document}\n\n')

    def write_preamble(self, out):
        # If we're generating an index, add it to the preamble.
        options = []
        options.append('creator={epydoc %s}' % epydoc.__version__)
        options.append('title={%s}' % plaintext_to_latex(self._prj_name or ''))
        if self._index: options.append('index')
        if self._hyperlink: options.append('hyperlink')
        out('\n'.join(self.PREAMBLE) % (','.join(options),
                                        self._epydoc_sty_package) + '\n')
        
        # Set the encoding.
        out('\\usepackage[%s]{inputenc}\n' % self.get_latex_encoding())

        # If we're generating hyperrefs, add the appropriate packages.
        # Note: this needs to be the last thing in the preamble -JEG
        if self._hyperref:
            out('\\definecolor{UrlColor}{rgb}{0,0.08,0.45}\n')
            
        # If restructuredtext was used, then we need to extend
        # the prefix to include LatexTranslator.head_prefix.
        if 'restructuredtext' in epydoc.markup.MARKUP_LANGUAGES_USED:
            from epydoc.markup import restructuredtext
            rst_head = restructuredtext.latex_head_prefix()
            rst_head = ''.join(rst_head).split('\n')
            for line in rst_head[1:]:
                m = re.match(r'\\usepackage(\[.*?\])?{(.*?)}', line)
                if m and m.group(2) in (
                    'babel', 'hyperref', 'color', 'alltt', 'parskip',
                    'fancyhdr', 'boxedminipage', 'makeidx',
                    'multirow', 'longtable', 'tocbind', 'assymb',
                    'fullpage', 'inputenc'):
                    pass
                else:
                    out(line+'\n')

        
    #////////////////////////////////////////////////////////////
    #{ Chapters
    #////////////////////////////////////////////////////////////

    def write_module(self, out, doc):
        self.write_header(out, doc)
        out(self.start_of('Section Heading', doc))

        # Add this module to the index.
        out(self.indexterm(doc, 'start'))

        # Add a section marker.
        out(self.section('%s %s' % (self.doc_kind(doc),
                                    _dotted(doc.canonical_name)),
                         ref=doc))

        # Add the module's description.
        if doc.descr not in (None, UNKNOWN):
            out(self.start_of('Description', doc))
            out('\\begin{EpydocModuleDescription}%\n')
            out(self.docstring_to_latex(doc.descr, doc, 4))
            out('\\end{EpydocModuleDescription}\n')

        # Add version, author, warnings, requirements, notes, etc.
        out(self.metadata(doc))

        # If it's a package, list the sub-modules.
        if (self._list_submodules and self._show_submodule_list and
            doc.submodules != UNKNOWN and doc.submodules):
            self.write_module_list(out, doc)

        # Contents.
        if self._list_classes_separately:
            self.write_class_list(out, doc)
        self.write_list(out, 'Functions', doc, 'EpydocFunctionList',
                        'function')
        self.write_list(out, 'Variables', doc, 'EpydocVariableList', 'other')

        # Class list.
        if not self._list_classes_separately:
            classes = doc.select_variables(imported=False, value_type='class',
                                           public=self._public_filter)
            if classes:
                out(self.start_of('Classes', doc))
                for var_doc in classes:
                    # don't use \include -- can't be nested.
                    out('\\input{%s-class}\n' % var_doc.value.canonical_name)

        # Mark the end of the module (for the index)
        out(self.start_of('Footer', doc))
        out(self.indexterm(doc, 'end'))

    def render_graph(self, graph):
        if graph is None: return ''
        graph.caption = graph.title = None
        return graph.to_latex(self._directory) or ''

    def write_class(self, out, doc):
        self.write_header(out, doc)
        out(self.start_of('Section Heading', doc))

        # Add this class to the index.
        out(self.indexterm(doc, 'start'))

        # Decide on our short (contextualized) name.
        if self._list_classes_separately:
            short_name = doc.canonical_name
        if doc.defining_module not in (None, UNKNOWN):
            short_name = doc.canonical_name.contextualize(
                doc.defining_module.canonical_name)
        else:
            short_name = doc.canonical_name[-1]

        # Decidie on our initial section level.
        if self._list_classes_separately:
            seclevel = 0
        else:
            seclevel = 1

        # Add a section marker.
        out(self.section('%s %s' % (self.doc_kind(doc), _dotted(short_name)),
                         seclevel, ref=doc))

        # Display our base classes & subclasses
        out(self.start_of('Class Tree', doc))
        if ((doc.bases not in (UNKNOWN, None) and len(doc.bases) > 0) or
            (doc.subclasses not in (UNKNOWN,None) and len(doc.subclasses)>0)):
            # Display bases graphically, if requested.
            if 'umlclasstree' in self._graph_types:
                graph = uml_class_tree_graph(doc, self._docstring_linker, doc)
                out(self.render_graph(graph))
                
            elif 'classtree' in self._graph_types:
                graph = class_tree_graph([doc], self._docstring_linker, doc)
                out(self.render_graph(graph))

            # Otherwise, use ascii-art.
            else:
        
                # Add our base list.
                if doc.bases not in (UNKNOWN, None) and len(doc.bases) > 0:
                    out(self.base_tree(doc))

            # The class's known subclasses
            if (doc.subclasses not in (UNKNOWN, None) and
                len(doc.subclasses) > 0):
                sc_items = [_hyperlink(sc, '%s' % sc.canonical_name)
                            for sc in doc.subclasses]
                out('{\\raggedright%\n')
                out(self._descrlist(sc_items, 'Known Subclasses', short=1))
                out('}%\n')

        # The class's description.
        if doc.descr not in (None, UNKNOWN):
            out(self.start_of('Description', doc))
            out('\\begin{EpydocClassDescription}%\n')
            out(self.docstring_to_latex(doc.descr, doc, 4))
            out('\\end{EpydocClassDescription}\n')

        # Version, author, warnings, requirements, notes, etc.
        out(self.metadata(doc))

        # Contents.
        self.write_list(out, 'Methods', doc, 'EpydocFunctionList',
                         'method', seclevel+1)
        self.write_list(out, 'Properties', doc, 'EpydocPropertyList',
                        'property', seclevel+1)
        self.write_list(out, 'Class Variables', doc,
                        'EpydocClassVariableList',
                        'classvariable', seclevel+1)
        self.write_list(out, 'Instance Variables', doc,
                        'EpydocInstanceVariableList',
                        'instancevariable', seclevel+1)

        # Mark the end of the class (for the index)
        out(self.start_of('Footer', doc))
        out(self.indexterm(doc, 'end'))

        # Write any nested classes.  These will have their own
        # section (at the same level as this section)
        if not self._list_classes_separately:
            nested_classes = doc.select_variables(
                    imported=False, value_type='class',
                    public=self._public_filter)
            if nested_classes:
                out(self.start_of('Nested Classes', doc))
                for nested_class in nested_classes:
                    if (nested_class.value.canonical_name != UNKNOWN and
                        (nested_class.value.canonical_name[:-1] ==
                         doc.canonical_name)):
                        # don't use \include -- can't be nested.
                        out('\\input{%s-class}\n' %
                            nested_class.value.canonical_name)

    #////////////////////////////////////////////////////////////
    #{ Module hierarchy trees
    #////////////////////////////////////////////////////////////
    
    def write_module_tree(self, out):
        modules = [doc for doc in self.valdocs
                   if isinstance(doc, ModuleDoc)]
        if not modules: return
        
        # Write entries for all top-level modules/packages.
        out('\\begin{itemize}\n')
        out('\\setlength{\\parskip}{0ex}\n')
        for doc in modules:
            if (doc.package in (None, UNKNOWN) or
                doc.package not in self.valdocs):
                self.write_module_tree_item(out, doc)
        return s +'\\end{itemize}\n'

    def write_module_list(self, out, doc):
        if len(doc.submodules) == 0: return
        out(self.start_of('Submodules', doc))
        
        out(self.section('Submodules', 1))
        out('\\begin{EpydocModuleList}\n')

        for group_name in doc.group_names():
            if not doc.submodule_groups[group_name]: continue
            if group_name:
                out('  \\EpydocGroup{%s}\n' % group_name)
                out('  \\begin{EpydocModuleList}\n')
            for submodule in doc.submodule_groups[group_name]:
                self.write_module_tree_item(out, submodule)
            if group_name:
                out('  \end{EpydocModuleList}\n')

        out('\\end{EpydocModuleList}\n\n')

    def write_module_tree_item(self, out, doc, depth=0):
        """
        Helper function for L{write_module_tree} and L{write_module_list}.
        
        @rtype: C{string}
        """
        out(' '*depth + '\\item[%s]\n' %
            _hyperlink(doc, doc.canonical_name[-1]))

        if doc.summary not in (None, UNKNOWN):
            out(self.docstring_to_latex(doc.summary, doc, depth+2))
        out(self.crossref(doc) + '\n\n')
        if doc.submodules != UNKNOWN and doc.submodules:
            out(' '*depth + '  \\begin{EpydocModuleList}\n')
            for submodule in doc.submodules:
                self.write_module_tree_item(out, submodule, depth+4)
            out(' '*depth + '  \\end{EpydocModuleList}\n')

    #////////////////////////////////////////////////////////////
    #{ Base class trees
    #////////////////////////////////////////////////////////////

    def base_tree(self, doc, width=None, linespec=None):
        if width is None:
            width = self._find_tree_width(doc)+2
            linespec = []
            s = ('  %% Class tree line for this class (%s)\n  ' %
                 doc.canonical_name + '&'*(width-4) +
                 '\\multicolumn{2}{l}{\\textbf{%s}}\n' %
                   _dotted('%s'%self._base_name(doc)))
            s += '\\end{tabular}\n\n'
            top = 1
        else:
            s = self._base_tree_line(doc, width, linespec)
            top = 0
        
        if isinstance(doc, ClassDoc):
            for i in range(len(doc.bases)-1, -1, -1):
                base = doc.bases[i]
                spec = (i > 0)
                s = self.base_tree(base, width, [spec]+linespec) + s

        if top:
            s = '\\begin{tabular}{%s}\n' % (width*'c') + s

        return s

    def _base_name(self, doc):
        if doc.canonical_name is None:
            if doc.parse_repr is not None:
                return doc.parse_repr
            else:
                return '??'
        else:
            return '%s' % doc.canonical_name

    def _find_tree_width(self, doc):
        if not isinstance(doc, ClassDoc): return 2
        width = 2
        for base in doc.bases:
            width = max(width, self._find_tree_width(base)+2)
        return width

    def _base_tree_line(self, doc, width, linespec):
        # linespec is a list of booleans.
        base_name = _dotted(self._base_name(doc))
        
        s = '  %% Class tree line for base "%s"\n' % self._base_name(doc)
        labelwidth = width-2*len(linespec)-2

        # The base class name.
        s += '  \\multicolumn{%s}{r}{\n' % labelwidth
        s += '      \\settowidth{\\EpydocBCL}{%s}\n' % base_name
        s += '      \\multirow{2}{\\EpydocBCL}{\n'
        s += '        %s}}\n' % _hyperlink(doc, self._base_name(doc))

        # The vertical bars for other base classes (top half)
        for vbar in linespec:
            if vbar: s += '    &&\\multicolumn{1}{|c}{}\n'
            else: s += '    &&\n'

        # The horizontal line.
        s += '    \\\\\\cline{%s-%s}\n' % (labelwidth+1, labelwidth+1)

        # The vertical bar for this base class.
        s += '    ' + '&'*labelwidth
        s += '\\multicolumn{1}{c|}{}\n'

        # The vertical bars for other base classes (bottom half)
        for vbar in linespec:
            if vbar: s += '    &\\multicolumn{1}{|c}{}&\n'
            else: s += '    &&\n'
        
        s += '    \\\\\n'

        return s
        
    #////////////////////////////////////////////////////////////
    #{ Class List
    #////////////////////////////////////////////////////////////
    
    def write_class_list(self, out, doc):
        groups = [(plaintext_to_latex(group_name),
                   doc.select_variables(group=group_name, imported=False,
                                        value_type='class',
                                        public=self._public_filter))
                  for group_name in doc.group_names()]

        # Discard any empty groups; and return if they're all empty.
        groups = [(g,vars) for (g,vars) in groups if vars]
        if not groups: return

        # Write a header.
        out(self.start_of('Classes', doc))
        out(self.section('Classes', 1))
        out('\\begin{EpydocClassList}\n')

        for name, var_docs in groups:
            if name:
                out('  \\EpydocGroup{%s}\n' % name)
                out('  \\begin{EpydocClassList}\n')
            # Add the lines for each class
            for var_doc in var_docs:
                self.write_class_list_line(out, var_doc)
            if name:
                out('  \\end{EpydocClassList}\n')

        out('\\end{EpydocClassList}\n')

    def write_class_list_line(self, out, var_doc):
        if var_doc.value in (None, UNKNOWN): return # shouldn't happen
        doc = var_doc.value
        out('  ' + '\\item[%s]' % _hyperlink(var_doc.target, 
                                             var_doc.name))
        if doc.summary not in (None, UNKNOWN):
            out(': %\n' + self.docstring_to_latex(doc.summary, doc))
        out(self.crossref(doc))
        
    #////////////////////////////////////////////////////////////
    #{ Details Lists
    #////////////////////////////////////////////////////////////
    
    # Also used for the property list.
    def write_list(self, out, heading, doc, list_type,
                       value_type, seclevel=1):
        # Divide all public variables of the given type into groups.
        groups = [(plaintext_to_latex(group_name),
                   doc.select_variables(group=group_name, imported=False,
                                        value_type=value_type,
                                        public=self._public_filter))
                  for group_name in doc.group_names()]

        # Discard any empty groups; and return if they're all empty.
        groups = [(g,vars) for (g,vars) in groups if vars]
        if not groups: return

        # Write a header.
        out(self.start_of(heading, doc))
        out(self.section(heading, seclevel))

        out('\\begin{%s}\n' % list_type)

        # Write a section for each group.
        grouped_inh_vars = {}
        for name, var_docs in groups:
            self.write_list_group(out, doc, name, var_docs, grouped_inh_vars)

        # Write a section for each inheritance pseudo-group (used if
        # inheritance=='grouped')
        if grouped_inh_vars:
            for base in doc.mro():
                if base in grouped_inh_vars:
                    hdr = ('Inherited from %s' %
                           plaintext_to_latex('%s' % base.canonical_name))
                    out(self.crossref(base) + '\n\n')
                    out('\\EpydocGroup{%s}\n' % hdr)
                    for var_doc in grouped_inh_vars[base]:
                        if isinstance(var_doc.value, RoutineDoc):
                            self.write_function(out, var_doc)
                        elif isinstance(var_doc.value, PropertyDoc):
                            self.write_property(out, var_doc)
                        else:
                            self.write_var(out, var_doc)
                            
        out('\\end{%s}\n\n' % list_type)

    def write_list_group(self, out, doc, name, var_docs, grouped_inh_vars):
        # Split up the var_docs list, according to the way each var
        # should be displayed:
        #   - listed_inh_vars -- for listed inherited variables.
        #   - grouped_inh_vars -- for grouped inherited variables.
        #   - normal_vars -- for all other variables.
        listed_inh_vars = {}
        normal_vars = []
        for var_doc in var_docs:
            if var_doc.container != doc:
                base = var_doc.container
                if (base not in self.class_set or
                    self._inheritance == 'listed'):
                    listed_inh_vars.setdefault(base,[]).append(var_doc)
                elif self._inheritance == 'grouped':
                    grouped_inh_vars.setdefault(base,[]).append(var_doc)
                elif self._inheritance == 'hidden':
                    pass
                else:
                    normal_vars.append(var_doc)
            else:
                normal_vars.append(var_doc)
            
        # Write a header for the group.
        if name:
            out('\\EpydocGroup{%s}\n' % name)
        # Write an entry for each object in the group:
        for var_doc in normal_vars:
            if isinstance(var_doc.value, RoutineDoc):
                self.write_function(out, var_doc)
            elif isinstance(var_doc.value, PropertyDoc):
                self.write_property(out, var_doc)
            else:
                self.write_var(out, var_doc)
        # Write a subsection for inherited objects:
        if listed_inh_vars:
            self.write_inheritance_list(out, doc, listed_inh_vars)
            
    def write_inheritance_list(self, out, doc, listed_inh_vars):
        for base in doc.mro():
            if base not in listed_inh_vars: continue
            #if str(base.canonical_name) == 'object': continue
            var_docs = listed_inh_vars[base]
            if self._public_filter:
                var_docs = [v for v in var_docs if v.is_public]
            if var_docs:
                out('\\EpydocInheritanceList{')
                out(plaintext_to_latex('%s' % base.canonical_name))
                out(self.crossref(base))
                out('}{')
                out(', '.join(['%s' % plaintext_to_latex(var_doc.name) +
                               self._parens_if_func(var_doc)
                               for var_doc in var_docs]))
                out('}\n')

    def _parens_if_func(self, var_doc):
        if isinstance(var_doc.value, RoutineDoc): return '()'
        else: return ''

    #////////////////////////////////////////////////////////////
    #{ Function Details
    #////////////////////////////////////////////////////////////

    def replace_par(self, out):
        def new_out(s):
            s = re.sub('(?m)\n([ \t]*\n)+', '\\par\n', s)
            s = re.sub(r'\\par\b', r'\\EpydocPar', s)
            s = re.sub(r'(?m)^([ \t]*)([^ \t].*)\\EpydocPar\n',
                       r'\1\2\n\1\\EpydocPar\n', s)
            out(s)
        return new_out
    
    def write_function(self, out, var_doc):
        func_doc = var_doc.value
        is_inherited = (var_doc.overrides not in (None, UNKNOWN))

        # Add the function to the index.  Note: this will actually
        # select the containing section, and won't give a reference
        # directly to the function.
        if not is_inherited:
            out('  %s' % self.indexterm(func_doc))

        out('  \\EpydocFunction{%% <<< %s >>>\n' % var_doc.name)

        # We're passing arguments using xkeyval, which is unhappy if
        # the arguments contain \par.  So replace every occurence of
        # \par with \EpydocPar (which is defined to just return \par).
        out = self.replace_par(out)
        
        # Argument 1: the function signature
        out('    signature={%%\n%s    }' %
            self.function_signature(var_doc))

        # Argument 2: the function description
        if func_doc.descr not in (None, UNKNOWN):
            out(',\n    description={%\n')
            out(self.docstring_to_latex(func_doc.descr, func_doc, 6))
            out('    }')

        # Argument 3: the function parameter descriptions
        if func_doc.arg_descrs or func_doc.arg_types:
            out(',\n    parameters={%\n')
            self.write_function_parameters(out, var_doc)
            out('    }')

        # Argument 4: The return description
        if func_doc.return_descr not in (None, UNKNOWN):
            out(',\n    returndescr={%\n')
            out(self.docstring_to_latex(func_doc.return_descr, func_doc, 6))
            out('    }')
        
        # Argument 5: The return type
        if func_doc.return_type not in (None, UNKNOWN):
            out(',\n    returntype={%\n')
            out(self.docstring_to_latex(func_doc.return_type, func_doc, 6))
            out('    }')

        # Argument 6: The raises section
        if func_doc.exception_descrs not in (None, UNKNOWN, [], ()):
            out(',\n    raises={%\n')
            out(' '*6+'\\begin{EpydocFunctionRaises}\n')
            for name, descr in func_doc.exception_descrs:
                out(' '*10+'\\item[%s]\n\n' %
                    plaintext_to_latex('%s' % name))
                out(self.docstring_to_latex(descr, func_doc, 10))
            out(' '*6+'\\end{EpydocFunctionRaises}\n')
            out('    }')

        # Argument 7: The overrides section
        if var_doc.overrides not in (None, UNKNOWN):
            out(',\n    overrides={%\n')
            out('\\EpydocFunctionOverrides')
            if (func_doc.docstring in (None, UNKNOWN) and
                var_doc.overrides.value.docstring not in (None, UNKNOWN)):
                out('[1]')
            out('{%s}\n' 
                % _hyperlink(var_doc.overrides, 
                             '%s' % var_doc.overrides.canonical_name))
            out('    }')

        # Argument 8: The metadata section
        metadata = self.metadata(func_doc, 6)
        if metadata:
            out(',\n    metadata={%%\n%s    }' % metadata)

        out('}%\n')

    def write_function_parameters(self, out, var_doc):
        func_doc = var_doc.value
        # Find the longest name.
        longest = max([0]+[len(n) for n in func_doc.arg_types])
        for names, descrs in func_doc.arg_descrs:
            longest = max([longest]+[len(n) for n in names])
        # Table header.
        out(' '*6+'\\begin{EpydocFunctionParameters}{%s}\n' % (longest*'x'))
        # Add params that have @type but not @param info:
        arg_descrs = list(func_doc.arg_descrs)
        args = set()
        for arg_names, arg_descr in arg_descrs:
            args.update(arg_names)
        for arg in var_doc.value.arg_types:
            if arg not in args:
                arg_descrs.append( ([arg],None) )
        # Display params
        for (arg_names, arg_descr) in arg_descrs:
            arg_name = plaintext_to_latex(', '.join(arg_names))
            out('%s\\item[%s]\n' % (' '*8, arg_name))
            if arg_descr:
                out(self.docstring_to_latex(arg_descr, func_doc, 10))
            # !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            # !!! JEG - this loop needs abstracting
            # !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            for arg_name in arg_names:
                arg_typ = func_doc.arg_types.get(arg_name)
                if arg_typ is not None:
                    if len(arg_names) == 1:
                        lhs = 'type'
                    else:
                        lhs = 'type of %s' % arg_name
                    rhs = self.docstring_to_latex(arg_typ, func_doc, 14)
                    out('%s\\textit{ (%s=%%\n%s%s)}\n' % (' '*12, lhs,
                                                      rhs, ' '*12))
        out(' '*6+'\\end{EpydocFunctionParameters}\n')
        
    def function_signature(self, var_doc, indent=6):
        func_doc = var_doc.value
        func_name = var_doc.name

        s = ('%s\\begin{EpydocFunctionSignature}%%\n%s  {%s}%%\n' %
             (indent*' ', indent*' ', _hypertarget(var_doc, func_name)))
      
        # This should never happen, but just in case:
        if func_doc not in (None, UNKNOWN):
            if func_doc.posargs == UNKNOWN:
                args = ['\\GenericArg{}']
            else:
                args = [self.func_arg(name, default) for (name, default)
                        in zip(func_doc.posargs, func_doc.posarg_defaults)]
            if func_doc.vararg:
                if func_doc.vararg == '...':
                    args.append('\\GenericArg{}')
                else:
                    args.append('\\VarArg{%s}' %
                                plaintext_to_latex(func_doc.vararg))
            if func_doc.kwarg:
                args.append('\\KWArg{%s}' % plaintext_to_latex(func_doc.kwarg))

        argindent = (indent*' '+'    ')
        s += argindent+('%%\n%s\\and' % argindent).join(args)+'%\n'
        s += indent*' '+'\\end{EpydocFunctionSignature}%\n'
      
        return s

    def func_arg(self, name, default):
        s = '\\Param'
        
        if default is not None:
            s += "[%s]" % default.summary_pyval_repr().to_latex(None)
        s += '{%s}' % self._arg_name(name)

        return s

    def _arg_name(self, arg):
        if isinstance(arg, basestring):
            return plaintext_to_latex(arg)
        else:
            return '\\TupleArg{%s}' % '\\and '.join([self._arg_name(a)
                                                     for a in arg])

    #////////////////////////////////////////////////////////////
    #{ Variable Details
    #////////////////////////////////////////////////////////////

    def write_var(self, out, var_doc):
        # We're passing arguments using xkeyval, which is unhappy if
        # the arguments contain \par.  So replace every occurence of
        # \par with \EpydocPar (which is defined to just return \par).
        out = self.replace_par(out)
        
        has_descr = var_doc.descr not in (None, UNKNOWN)
        has_type = var_doc.type_descr not in (None, UNKNOWN)
        has_repr = (var_doc.value not in (None, UNKNOWN) and
                    (var_doc.value.parse_repr is not UNKNOWN or
                     var_doc.value.pyval_repr() is not UNKNOWN))

        out('  \\EpydocVariable{%% <<< %s >>>\n' % var_doc.name)
        out('    name={%s}' % _hypertarget(var_doc, var_doc.name))
        if has_descr:
            out(',\n    description={%%\n%s    }' %
                self.docstring_to_latex(var_doc.descr, var_doc, 6))
        if has_type:
            out(',\n    type={%%\n%s    }' %
                self.docstring_to_latex(var_doc.type_descr, var_doc, 6))
        if has_repr:
            out(',\n    value={%s}' %
                var_doc.value.summary_pyval_repr().to_latex(None))
        metadata = self.metadata(var_doc, 6)
        if metadata:
            out(',\n    metadata={%%\n%s    }' % metadata)
        out('}%\n')

    #////////////////////////////////////////////////////////////
    #{ Property Details
    #////////////////////////////////////////////////////////////

    def write_property(self, out, var_doc):
        # We're passing arguments using xkeyval, which is unhappy if
        # the arguments contain \par.  So replace every occurence of
        # \par with \EpydocPar (which is defined to just return \par).
        out = self.replace_par(out)
        
        prop_doc = var_doc.value
        has_descr = prop_doc.descr not in (None, UNKNOWN)
        has_type = prop_doc.type_descr not in (None, UNKNOWN)
        
        out('  \\EpydocProperty{%% <<< %s >>>\n' % var_doc.name)
        out('    name={%s}' % _hypertarget(var_doc, var_doc.name))
        if has_descr:
            out(',\n    description={%%\n%s    }' %
                self.docstring_to_latex(prop_doc.descr, prop_doc, 6))
        if has_type:
            out(',\n    type={%%\n%s    }' %
                self.docstring_to_latex(prop_doc.type_descr, prop_doc, 6))
        # [xx] What if the accessor is private and show_private=False?
        for accessor in ('fget', 'fset', 'fdel'):
            accessor_func = getattr(prop_doc, accessor)
            if (accessor_func not in (None, UNKNOWN) and
                not accessor_func.canonical_name[0].startswith('??')):
                if isinstance(accessor_func, RoutineDoc):
                    suffix = '()'
                else:
                    suffix = ''
                out(',\n    %s={%s%s}' %
                    (accessor, _dotted(accessor_func.canonical_name), suffix))
        metadata = self.metadata(prop_doc, 6)
        if metadata:
            out(',\n    metadata={%%\n%s    }' % metadata)
        out('}%\n')

    #////////////////////////////////////////////////////////////
    #{ Standard Fields
    #////////////////////////////////////////////////////////////

    def metadata(self, doc, indent=0):
        fields = []
        field_values = {}
        s = ''
        
        for (field, arg, descr) in doc.metadata:
            if field not in field_values:
                fields.append(field)
            if field.takes_arg:
                subfields = field_values.setdefault(field,{})
                subfields.setdefault(arg,[]).append(descr)
            else:
                field_values.setdefault(field,[]).append(descr)

        if fields and indent == 0:
            s += self.start_of('Metadata', doc)
            
        for field in fields:
            if field.takes_arg:
                for arg, descrs in field_values[field].items():
                    s += self.meatadata_field(doc, field, descrs,
                                              indent, arg)
            else:
                s += self.meatadata_field(doc, field, field_values[field],
                                          indent)
        return s
                                          

    def meatadata_field(self, doc, field, descrs, indent, arg=''):
        singular = field.singular
        plural = field.plural
        if arg:
            singular += ' (%s)' % arg
            plural += ' (%s)' % arg
        return (' '*indent + '%% %s:\n' % field.singular +
                self._descrlist([self.docstring_to_latex(d, doc, indent+2)
                                 for d in descrs],
                                field.singular, field.plural, field.short,
                                indent))

    # [xx] ignores indent for now
    def _descrlist(self, items, singular, plural=None, short=0, indent=0):
        ind = indent*' '
        if plural is None: plural = singular
        if len(items) == 0: return ''
        if len(items) == 1 and singular is not None:
            return ('%s\\EpydocMetadataSingleValue{%s}{\n%s%s}\n' %
                    (ind, singular, items[0], ind))
        if short:
            s = '%s\\begin{EpydocMetadataShortList}{%s}\n' % (ind, plural)
            s += ('%s\\and\n' % ind).join(items)
            s += '%s\\end{EpydocMetadataShortList}\n' % ind
            return s
        else:
            s = '%s\\begin{EpydocMetadataLongList}{%s}\n' % (ind, plural)
            s += ''.join(['%s  \item\n%s' % (ind,item) for item in items])
            s += '%s\\end{EpydocMetadataLongList}\n' % ind
            return s


    #////////////////////////////////////////////////////////////
    #{ Docstring -> LaTeX Conversion
    #////////////////////////////////////////////////////////////

    # We only need one linker, since we don't use context:
    class _LatexDocstringLinker(markup.DocstringLinker):
        def translate_indexterm(self, indexterm):
            indexstr = re.sub(r'["!|@]', r'"\1', indexterm.to_latex(self))
            return ('\\index{%s}\\textit{%s}' % (indexstr, indexstr))
        def translate_identifier_xref(self, identifier, label=None):
            if label is None: label = markup.plaintext_to_latex(identifier)
            return '\\texttt{%s}' % label
                
    _docstring_linker = _LatexDocstringLinker()
    
    def docstring_to_latex(self, docstring, where, indent=0, breakany=0):
        """
        Return a latex string that renders the given docstring.  This
        string expects to start at the beginning of a line; and ends
        with a newline.
        """
        if docstring is None: return ''
        s = docstring.to_latex(self._docstring_linker, indent=indent+2,
                               directory=self._directory,
                               docindex=self.docindex,
                               context=where,
                               hyperref=self._hyperref)
        return (' '*indent + '\\begin{EpydocDescription}\n' +
                ' '*indent + '  ' + s.strip() + '%\n' +
                ' '*indent + '\\end{EpydocDescription}\n')
    
    #////////////////////////////////////////////////////////////
    #{ Helpers
    #////////////////////////////////////////////////////////////

    def write_header(self, out, where):
        out('%\n% API Documentation')
        if self._prj_name: out(' for %s' % self._prj_name)
        if isinstance(where, APIDoc):
            out('\n%% %s %s' % (self.doc_kind(where), where.canonical_name))
        else:
            out('\n%% %s' % where)
        out('\n%%\n%% Generated by epydoc %s\n' % epydoc.__version__)
        out('%% [%s]\n%%\n' % time.asctime(time.localtime(time.time())))

    def start_of(self, section_name, doc=None):
        return ('\n' + 75*'%' + '\n' +
                '%%' + section_name.center(71) + '%%\n' +
                75*'%' + '\n\n')

    def section(self, title, depth=0, ref=None):
        sec = self.SECTIONS[depth+self._top_section]
        text = (sec % title) + '%\n'
        if ref:
            text += _hypertarget(ref, "") + '%\n'
        return text

    # [xx] not used:
    def sectionstar(self, title, depth, ref=None):
        sec = self.STARSECTIONS[depth+self._top_section]
        text = (sec % title) + '%\n'
        if ref:
            text += _hypertarget(ref, "") + '%\n'
        return text

    def doc_kind(self, doc):
        if isinstance(doc, ModuleDoc) and doc.is_package == True:
            return 'Package'
        elif (isinstance(doc, ModuleDoc) and
              doc.canonical_name[0].startswith('script')):
            return 'Script'
        elif isinstance(doc, ModuleDoc):
            return 'Module'
        elif isinstance(doc, ClassDoc):
            return 'Class'
        elif isinstance(doc, ClassMethodDoc):
            return 'Class Method'
        elif isinstance(doc, StaticMethodDoc):
            return 'Static Method'
        elif isinstance(doc, RoutineDoc):
            if isinstance(self.docindex.container(doc), ClassDoc):
                return 'Method'
            else:
                return 'Function'
        else:
            return 'Variable'

    # [xx] list modules, classes, and functions as top-level index
    # items.  Methods are listed under their classes.  Nested classes
    # are listed under their classes.
    def indexterm(self, doc, pos='only', indent=0):
        """Return a latex string that marks the given term or section
        for inclusion in the index.  This string ends with a newline."""
        if not self._index: return ''
        if isinstance(doc, RoutineDoc) and not self._index_functions:
            return ''

        pieces = []
        kinds = []
        while True:
            if doc.canonical_name in (None, UNKNOWN): return '' # Give up.
            pieces.append(doc.canonical_name[-1])
            kinds.append(self.doc_kind(doc).lower())
            doc = self.docindex.container(doc)
            if isinstance(doc, ModuleDoc): break
            if doc is None: break
            if doc == UNKNOWN: return '' # give up.

        pieces.reverse()
        kinds.reverse()
        for i in range(1, len(pieces)):
            if not kinds[i].endswith('method'):
                pieces[i] = '%s.%s' % (pieces[i-1], pieces[i])
        pieces = ['\\EpydocIndex{%s}{%s}{%s}' %
                  (_dotted(piece.lower()), _dotted(piece), kind)
                  for (piece, kind) in zip (pieces, kinds)]

        if pos == 'only': modifier = ''
        elif pos == 'start': modifier = '|('
        elif pos == 'end': modifier = '|)'
        else: raise AssertionError('Bad index position %s' % pos)

        return '%s\\index{%s%s}%%\n' % (' '*indent, '!'.join(pieces), modifier)

    #: Map the Python encoding representation into mismatching LaTeX ones.
    latex_encodings = {
        'utf-8': 'utf8x',
    }

    def get_latex_encoding(self):
        """
        @return: The LaTeX representation of the selected encoding.
        @rtype: C{str}
        """
        enc = self._encoding.lower()
        return self.latex_encodings.get(enc, enc)

    def crossref(self, doc, indent=0):
        if (self._show_crossrefs and
            ((isinstance(doc, ModuleDoc) and doc in self.module_set) or
             (isinstance(doc, ClassDoc) and doc in self.class_set))):
            return '%s\\CrossRef{%s}%%\n' % (' '*indent, _label(doc),)
        else:
            return ''
        
def _label(doc):
    # Convert to a string & replace . and _.
    s = '%s' % doc.canonical_name
    s = s.replace('.', ':').replace('_','-')
    
    # Get rid of any other characters.  This is necessary only if
    # we're processing a script (whose name can be just about
    # anything), an unreachable value, or an object whose name has
    # been mangled with black magic.
    s = re.sub('[^\w:-]', '-', s)
    
    return s

# [xx] this should get used more often than it does, I think:
def _hyperlink(target, name):
    return '\\EpydocHyperlink{%s}{%s}' % (_label(target), _dotted(name))

def _hypertarget(uid, sig):
    return '\\EpydocHypertarget{%s}{%s}' % (_label(uid), _dotted(sig))

def _dotted(name):
    if not name: return ''
    return '\\EpydocDottedName{%s}' % plaintext_to_latex('%s' % name)

LATEX_WARNING_RE = re.compile('|'.join([
    r'(?P<file>\([\.a-zA-Z_\-/\\0-9]+[.\n][a-z]{2,3}\b)',
    (r'(?P<pkgwarn>^(Package|Latex) (?P<pkgname>[\w-]+) '+
     r'Warning:[^\n]*\n(\((?P=pkgname)\)[^\n]*\n)*)'),
    r'(?P<overfull>^(Overfull|Underfull)[^\n]*\n[^\n]*)',
    r'(?P<latexwarn>^LaTeX\s+Warning:\s+[^\n]*)',
    r'(?P<otherwarn>^[^\n]*Warning:[^\n]*)',
    r'(?P<paren>[()])',
    r'(?P<pageno>\[\d+({[^\}]+})?\])']),
                              re.MULTILINE+re.IGNORECASE)

OVERFULL_RE = re.compile(
    r'(?P<typ>Underfull|Overfull)\s+\\(?P<boxtype>[vh]box)\s+'
    r'\((?P<size>\d+)[^\n\)]+\)[^\n]+\s+lines\s+'
    r'(?P<start>\d+)')

IGNORE_WARNING_REGEXPS = [
    re.compile(r'LaTeX\s+Font\s+Warning:\s+.*\n\(Font\)\s*using.*instead'),
    re.compile(r'LaTeX\s+Font\s+Warning:\s+Some\s+font\s+shapes\s+'
               r'were\s+not\s+available,\s+defaults\s+substituted.'),
    ]

def show_latex_warnings(s):
    s = re.sub('(.{79,79})\n', r'\1', s)

    #[xx] we should probably pay special attention to overfull \vboxes.
    overfull = underfull = 0
    filestack = ['latex']
    block = None
    BLOCK = 'LaTeX Warnings: %s'
    pageno = 1
    for m in LATEX_WARNING_RE.finditer(s):
        #log.debug(m.group())
        # Check if it's something we don't care about.
        for regexp in IGNORE_WARNING_REGEXPS:
            if regexp.match(m.group()):
                m = None; break
        if m is None: continue
        # LaTeX started reading a new file:
        if m.group('file'):
            filename = ''.join(m.group('file')[1:].split())
            filename = re.sub(r'^\./', '', filename)
            if filename == 'api.toc': filename = 'Table of contents (api.toc)'
            if filename == 'api.ind': filename = 'Index (api.ind)'
            filestack.append(filename)
            if block is not None: epydoc.log.end_block()
            epydoc.log.start_block(BLOCK % filename)
            block = filename
        # LaTeX started writing a new page
        elif m.group('pageno'):
            if pageno == int(m.group()[1:-1].split('{')[0]):
                pageno += 1
        # LateX reported an overfull/underfull warning:
        elif m.group('overfull'):
            msg = m.group('overfull').strip().split('\n')[0]
            msg = re.sub(r'(\d+)\.\d+', r'\1', msg)
            msg = re.sub(r'(lines \d+)--(\d+)', r'\1-\2', msg)
            if msg.lower().startswith('overfull'): overfull += 1
            else: underfull += 1
            log.warning(msg)#+' (page %d)' % pageno)
#             m2 = OVERFULL_RE.match(msg)
#             if m2:
#                 if m2.group('boxtype') == 'vbox':
#                     log.warning(msg)
#                 elif (m2.group('typ').lower()=='overfull' and
#                       int(m2.group('size')) > 50):
#                     log.warning(msg)
#                 else:
#                     log.debug(msg)
#             else:
#                 log.debug(msg)
        # Latex reported a warning:
        elif m.group('latexwarn'):
            log.warning(m.group('latexwarn').strip()+' (page %d)' % pageno)
        # A package reported a warning:
        elif m.group('pkgwarn'):
            log.warning(m.group('pkgwarn').strip())
        else:
            # Display anything else that looks like a warning:
            if m.group('otherwarn'):
                log.warning(m.group('otherwarn').strip())
            # Update to account for parens.
            n = m.group().count('(') - m.group().count(')')
            if n > 0: filestack += [None] * n
            if n < 0: del filestack[n:]
            # Don't let filestack become empty:
            if not filestack: filestack.append('latex')
            if (filestack[-1] is not None and
                block is not None and block != filestack[-1]):
                epydoc.log.end_block()
                epydoc.log.start_block(BLOCK % filestack[-1])

    if block:
        epydoc.log.end_block()
            
#     if overfull or underfull:
#         msgs = []
#         if overfull == 1: msgs.append('1 overfull box')
#         elif overfull: msgs.append('%d overfull boxes' % overfull)
#         if underfull == 1: msgs.append('1 underfull box')
#         elif underfull: msgs.append('%d underfull boxes' % underfull)
#         log.warning('LaTeX reported %s' % ' and '.join(msgs))

#log.register_logger(log.SimpleLogger(log.DEBUG))
#show_latex_warnings(open('/tmp/po.test').read())
