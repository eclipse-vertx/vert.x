# epydoc -- Graph generation
#
# Copyright (C) 2005 Edward Loper
# Author: Edward Loper <edloper@loper.org>
# URL: <http://epydoc.sf.net>
#
# $Id: dotgraph.py 1805 2008-02-27 20:24:06Z edloper $

"""
Render Graphviz directed graphs as images.  Below are some examples.

.. importgraph::

.. classtree:: epydoc.apidoc.APIDoc

.. packagetree:: epydoc

:see: `The Graphviz Homepage
       <http://www.research.att.com/sw/tools/graphviz/>`__
"""
__docformat__ = 'restructuredtext'

import re
import sys
import tempfile
from epydoc import log
from epydoc.apidoc import *
from epydoc.util import *
from epydoc.compat import * # Backwards compatibility

#: Should the dot2tex module be used to render dot graphs to latex
#: (if it's available)?  This is experimental, and not yet working,
#: so it should be left False for now.
USE_DOT2TEX = False

#: colors for graphs of APIDocs
COLOR = dict(
    MODULE_BG = '#d8e8ff',
    CLASS_BG = '#d8ffe8',
    SELECTED_BG = '#ffd0d0',
    BASECLASS_BG = '#e0b0a0',
    SUBCLASS_BG = '#e0b0a0',
    UNDOCUMENTED_BG = '#c0c0c0',
    ROUTINE_BG = '#e8d0b0', # not used
    INH_LINK = '#800000',
    )

######################################################################
#{ Dot Graphs
######################################################################

DOT_COMMAND = 'dot'
"""The command that should be used to spawn dot"""

class DotGraph(object):
    """
    A ``dot`` directed graph.  The contents of the graph are
    constructed from the following instance variables:

      - `nodes`: A list of `DotGraphNode`\\s, encoding the nodes
        that are present in the graph.  Each node is characterized
        a set of attributes, including an optional label.
      - `edges`: A list of `DotGraphEdge`\\s, encoding the edges
        that are present in the graph.  Each edge is characterized
        by a set of attributes, including an optional label.
      - `node_defaults`: Default attributes for nodes.
      - `edge_defaults`: Default attributes for edges.
      - `body`: A string that is appended as-is in the body of
        the graph.  This can be used to build more complex dot
        graphs.

    The `link()` method can be used to resolve crossreference links
    within the graph.  In particular, if the 'href' attribute of any
    node or edge is assigned a value of the form ``<name>``, then it
    will be replaced by the URL of the object with that name.  This
    applies to the `body` as well as the `nodes` and `edges`.

    To render the graph, use the methods `write()` and `render()`.
    Usually, you should call `link()` before you render the graph.
    """
    _uids = set()
    """A set of all uids that that have been generated, used to ensure
    that each new graph has a unique uid."""

    DEFAULT_NODE_DEFAULTS={'fontsize':10, 'fontname': 'Helvetica'}
    DEFAULT_EDGE_DEFAULTS={'fontsize':10, 'fontname': 'Helvetica'}
    
    DEFAULT_LATEX_SIZE="6.25,8"
    """The default minimum size in inches (width,height) for graphs
    when rendering with `to_latex()`"""
    
    DEFAULT_HTML_SIZE="10,20"
    """The default minimum size in inches (width,height) for graphs
    when rendering with `to_html()`"""
    
    DEFAULT_HTML_IMAGE_FORMAT = 'gif'
    """The default format used to generate images by `to_html()`"""
    
    def __init__(self, title, body='', node_defaults=None,
                 edge_defaults=None, caption=None):
        """
        Create a new `DotGraph`.
        """
        self.title = title
        """The title of the graph."""

        self.caption = caption
        """A caption for the graph."""
        
        self.nodes = []
        """A list of the nodes that are present in the graph.
        
        :type: ``list`` of `DotGraphNode`"""
        
        self.edges = []
        """A list of the edges that are present in the graph.
        
        :type: ``list`` of `DotGraphEdge`"""

        self.body = body
        """A string that should be included as-is in the body of the
        graph.
        
        :type: ``str``"""

        self.node_defaults = node_defaults or self.DEFAULT_NODE_DEFAULTS
        """Default attribute values for nodes."""
        
        self.edge_defaults = edge_defaults or self.DEFAULT_EDGE_DEFAULTS
        """Default attribute values for edges."""

        self.uid = re.sub(r'\W', '_', title).lower()
        """A unique identifier for this graph.  This can be used as a
        filename when rendering the graph.  No two `DotGraph`\s will
        have the same uid."""

        # Encode the title, if necessary.
        if isinstance(self.title, unicode):
            self.title = self.title.encode('ascii', 'xmlcharrefreplace')

        # Make sure the UID isn't too long.
        self.uid = self.uid[:30]
        
        # Make sure the UID is unique
        if self.uid in self._uids:
            n = 2
            while ('%s_%s' % (self.uid, n)) in self._uids: n += 1
            self.uid = '%s_%s' % (self.uid, n)
        self._uids.add(self.uid)

    def to_latex(self, directory, center=True, size=None):
        """
        Return the LaTeX code that should be used to display this
        graph.  Two image files will be written: image_file+'.eps'
        and image_file+'.pdf'.

        :param size: The maximum size for the generated image, in
            inches.  In particular, if ``size`` is ``\"w,h\"``, then
            this will add a line ``size=\"w,h\"`` to the dot graph.
            Defaults to `DEFAULT_LATEX_SIZE`.
        :type size: ``str``
        """
        eps_file = os.path.join(directory, self.uid+'.eps')
        pdf_file = os.path.join(directory, self.uid+'.pdf')
        size = size or self.DEFAULT_LATEX_SIZE
        # Use dot2tex if requested (and if it's available).
        # Otherwise, render it to an image file & use \includgraphics.
        if USE_DOT2TEX and dot2tex is not None:
            try: return self._to_dot2tex(center, size)
            except KeyboardInterrupt: raise
            except:
                raise
                log.warning('dot2tex failed; using dot instead')

        # Render the graph in postscript.
        ps = self._run_dot('-Tps', size=size)
        # Write the postscript output.
        psfile = open(eps_file, 'wb')
        psfile.write('%!PS-Adobe-2.0 EPSF-1.2\n')
        psfile.write(ps)
        psfile.close()
        # Use ps2pdf to generate the pdf output.
        try: run_subprocess(('ps2pdf', '-dEPSCrop', eps_file, pdf_file))
        except RunSubprocessError, e:
            log.warning("Unable to render Graphviz dot graph (%s):\n"
                            "ps2pdf failed." % self.title)
            return None
        
        # Generate the latex code to display the graph.
        s = '  \\includegraphics{%s}\n' % self.uid
        if center: s = '\\begin{center}\n%s\\end{center}\n' % s
        return s

    def _to_dot2tex(self, center=True, size=None):
        # requires: pgf, latex-xcolor.
        from dot2tex import dot2tex
        if 0: # DEBUG
            import logging
            log = logging.getLogger("dot2tex")
            log.setLevel(logging.DEBUG)
            console = logging.StreamHandler()
            formatter = logging.Formatter('%(levelname)-8s %(message)s')
            console.setFormatter(formatter)
            log.addHandler(console)
        options = dict(crop=True, autosize=True, figonly=True, debug=True)
        conv = dot2tex.Dot2PGFConv(options)
        s = conv.convert(self.to_dotfile(size=size))
        conv.dopreproc = False
        s = conv.convert(s)
        if center: s = '\\begin{center}\n%s\\end{center}\n' % s
        return s

    def to_html(self, directory, center=True, size=None):
        """
        Return the HTML code that should be uesd to display this graph
        (including a client-side image map).
        
        :param image_url: The URL of the image file for this graph;
            this should be generated separately with the `write()` method.
        :param size: The maximum size for the generated image, in
            inches.  In particular, if ``size`` is ``\"w,h\"``, then
            this will add a line ``size=\"w,h\"`` to the dot graph.
            Defaults to `DEFAULT_HTML_SIZE`.
        :type size: ``str``
        """
        image_url = '%s.%s' % (self.uid, self.DEFAULT_HTML_IMAGE_FORMAT)
        image_file = os.path.join(directory, image_url)
        size = size or self.DEFAULT_HTML_SIZE
        # If dotversion >1.8.10, then we can generate the image and
        # the cmapx with a single call to dot.  Otherwise, we need to
        # run dot twice.
        if get_dot_version() > [1,8,10]:
            cmapx = self._run_dot('-T%s' % self._pick_language(image_file),
                                  '-o%s' % image_file,
                                  '-Tcmapx', size=size)
            if cmapx is None: return '' # failed to render
        else:
            if not self.write(image_file):
                return '' # failed to render
            cmapx = self.render('cmapx') or ''

        # Decode the cmapx (dot uses utf-8)
        try:
            cmapx = cmapx.decode('utf-8')
        except UnicodeDecodeError:
            log.debug('%s: unable to decode cmapx from dot; graph will '
                      'not have clickable regions' % image_file)
            cmapx = ''

        title = plaintext_to_html(self.title or '')
        caption = plaintext_to_html(self.caption or '')
        if title or caption:
            css_class = 'graph-with-title'
        else:
            css_class = 'graph-without-title'
        if len(title)+len(caption) > 80:
            title_align = 'left'
            table_width = ' width="600"'
        else:
            title_align = 'center'
            table_width = ''
            
        if center: s = '<center>'
        if title or caption:
            s += ('<table border="0" cellpadding="0" cellspacing="0" '
                  'class="graph"%s>\n  <tr><td align="center">\n' %
                  table_width)
        s += ('  %s\n  <img src="%s" alt=%r usemap="#%s" '
              'ismap="ismap" class="%s" />\n' %
              (cmapx.strip(), image_url, title, self.uid, css_class))
        if title or caption:
            s += '  </td></tr>\n  <tr><td align=%r>\n' % title_align
            if title:
                s += '<span class="graph-title">%s</span>' % title
            if title and caption:
                s += ' -- '
            if caption:
                s += '<span class="graph-caption">%s</span>' % caption
            s += '\n  </td></tr>\n</table><br />'
        if center: s += '</center>'
        return s

    def link(self, docstring_linker):
        """
        Replace any href attributes whose value is ``<name>`` with 
        the url of the object whose name is ``<name>``.
        """
        # Link xrefs in nodes
        self._link_href(self.node_defaults, docstring_linker)
        for node in self.nodes:
            self._link_href(node.attribs, docstring_linker)

        # Link xrefs in edges
        self._link_href(self.edge_defaults, docstring_linker)
        for edge in self.nodes:
            self._link_href(edge.attribs, docstring_linker)

        # Link xrefs in body
        def subfunc(m):
            try: url = docstring_linker.url_for(m.group(1))
            except NotImplementedError: url = ''
            if url: return 'href="%s"%s' % (url, m.group(2))
            else: return ''
        self.body = re.sub("href\s*=\s*['\"]?<([\w\.]+)>['\"]?\s*(,?)",
                           subfunc, self.body)

    def _link_href(self, attribs, docstring_linker):
        """Helper for `link()`"""
        if 'href' in attribs:
            m = re.match(r'^<([\w\.]+)>$', attribs['href'])
            if m:
                try: url = docstring_linker.url_for(m.group(1))
                except NotImplementedError: url = ''
                if url: attribs['href'] = url
                else: del attribs['href']

    def write(self, filename, language=None, size=None):
        """
        Render the graph using the output format `language`, and write
        the result to `filename`.
        
        :return: True if rendering was successful.
        :param size: The maximum size for the generated image, in
            inches.  In particular, if ``size`` is ``\"w,h\"``, then
            this will add a line ``size=\"w,h\"`` to the dot graph.
            If not specified, no size line will be added.
        :type size: ``str``
        """
        if language is None: language = self._pick_language(filename)
        result = self._run_dot('-T%s' % language,
                               '-o%s' % filename,
                               size=size)
        # Decode into unicode, if necessary.
        if language == 'cmapx' and result is not None:
            result = result.decode('utf-8')
        return (result is not None)

    def _pick_language(self, filename):
            ext = os.path.splitext(filename)[1]
            if ext in ('.gif', '.png', '.jpg', '.jpeg'):
                return ext[1:]
            else:
                return 'gif'
                
    def render(self, language=None, size=None):
        """
        Use the ``dot`` command to render this graph, using the output
        format `language`.  Return the result as a string, or ``None``
        if the rendering failed.
        
        :param size: The maximum size for the generated image, in
            inches.  In particular, if ``size`` is ``\"w,h\"``, then
            this will add a line ``size=\"w,h\"`` to the dot graph.
            If not specified, no size line will be added.
        :type size: ``str``
        """
        return self._run_dot('-T%s' % language, size=size)

    def _run_dot(self, *options, **kwparam):
        if get_dot_version() == (0,): return None
        try:
            result, err = run_subprocess((DOT_COMMAND,)+options,
                                         self.to_dotfile(**kwparam))
            if err: log.warning("Graphviz dot warning(s):\n%s" % err)
        except OSError, e:
            log.warning("Unable to render Graphviz dot graph (%s):\n%s" %
                        (self.title, e))
            import tempfile, epydoc
            if epydoc.DEBUG:
                filename = tempfile.mktemp('.dot')
                out = open(filename, 'wb')
                out.write(self.to_dotfile(**kwparam))
                out.close()
                log.debug('Failed dot graph written to %s' % filename)
            return None

        return result

    def to_dotfile(self, size=None):
        """
        Return the string contents of the dot file that should be used
        to render this graph.
        
        :param size: The maximum size for the generated image, in
            inches.  In particular, if ``size`` is ``\"w,h\"``, then
            this will add a line ``size=\"w,h\"`` to the dot graph.
            If not specified, no size line will be added.
        :type size: ``str``
        """
        lines = ['digraph %s {' % self.uid,
                 'node [%s]' % ','.join(['%s="%s"' % (k,v) for (k,v)
                                         in self.node_defaults.items()]),
                 'edge [%s]' % ','.join(['%s="%s"' % (k,v) for (k,v)
                                         in self.edge_defaults.items()])]
        if size:
            lines.append('size="%s"' % size)
        if self.body:
            lines.append(self.body)
        lines.append('/* Nodes */')
        for node in self.nodes:
            lines.append(node.to_dotfile())
        lines.append('/* Edges */')
        for edge in self.edges:
            lines.append(edge.to_dotfile())
        lines.append('}')

        # Default dot input encoding is UTF-8
        return u'\n'.join(lines).encode('utf-8')

class DotGraphNode(object):
    _next_id = 0
    def __init__(self, label=None, html_label=None, **attribs):
        if label is not None and html_label is not None:
            raise ValueError('Use label or html_label, not both.')
        if label is not None: attribs['label'] = label
        self._html_label = html_label
        self._attribs = attribs
        self.id = DotGraphNode._next_id
        DotGraphNode._next_id += 1
        self.port = None

    def __getitem__(self, attr):
        return self._attribs[attr]

    def __setitem__(self, attr, val):
        if attr == 'html_label':
            self._attribs.pop('label')
            self._html_label = val
        else:
            if attr == 'label': self._html_label = None
            self._attribs[attr] = val

    def to_dotfile(self):
        """
        Return the dot commands that should be used to render this node.
        """
        attribs = ['%s="%s"' % (k,v) for (k,v) in self._attribs.items()
                   if v is not None]
        if self._html_label:
            attribs.insert(0, 'label=<%s>' % (self._html_label,))
        if attribs: attribs = ' [%s]' % (','.join(attribs))
        return 'node%d%s' % (self.id, attribs)

class DotGraphEdge(object):
    def __init__(self, start, end, label=None, **attribs):
        """
        :type start: `DotGraphNode`
        :type end: `DotGraphNode`
        """
        assert isinstance(start, DotGraphNode)
        assert isinstance(end, DotGraphNode)
        if label is not None: attribs['label'] = label
        self.start = start       #: :type: `DotGraphNode`
        self.end = end           #: :type: `DotGraphNode`
        self._attribs = attribs

    def __getitem__(self, attr):
        return self._attribs[attr]

    def __setitem__(self, attr, val):
        self._attribs[attr] = val

    def to_dotfile(self):
        """
        Return the dot commands that should be used to render this edge.
        """
        # Set head & tail ports, if the nodes have preferred ports.
        attribs = self._attribs.copy()
        if (self.start.port is not None and 'headport' not in attribs):
            attribs['headport'] = self.start.port
        if (self.end.port is not None and 'tailport' not in attribs):
            attribs['tailport'] = self.end.port
        # Convert attribs to a string
        attribs = ','.join(['%s="%s"' % (k,v) for (k,v) in attribs.items()
                            if v is not None])
        if attribs: attribs = ' [%s]' % attribs
        # Return the dotfile edge.
        return 'node%d -> node%d%s' % (self.start.id, self.end.id, attribs)

######################################################################
#{ Specialized Nodes for UML Graphs
######################################################################

class DotGraphUmlClassNode(DotGraphNode):
    """
    A specialized dot graph node used to display `ClassDoc`\s using
    UML notation.  The node is rendered as a table with three cells:
    the top cell contains the class name; the middle cell contains a
    list of attributes; and the bottom cell contains a list of
    operations::

         +-------------+
         |  ClassName  |
         +-------------+
         | x: int      |
         |     ...     |
         +-------------+
         | f(self, x)  |
         |     ...     |
         +-------------+

    `DotGraphUmlClassNode`\s may be *collapsed*, in which case they are
    drawn as a simple box containing the class name::
    
         +-------------+
         |  ClassName  |
         +-------------+
         
    Attributes with types corresponding to documented classes can
    optionally be converted into edges, using `link_attributes()`.

    :todo: Add more options?
      - show/hide operation signature
      - show/hide operation signature types
      - show/hide operation signature return type
      - show/hide attribute types
      - use qualifiers
    """
    def __init__(self, class_doc, linker, context, collapsed=False,
                 bgcolor=COLOR['CLASS_BG'], **options):
        """
        Create a new `DotGraphUmlClassNode` based on the class
        `class_doc`.

        :Parameters:
            `linker` : `markup.DocstringLinker`
                Used to look up URLs for classes.
            `context` : `APIDoc`
                The context in which this node will be drawn; dotted
                names will be contextualized to this context.
            `collapsed` : ``bool``
                If true, then display this node as a simple box.
            `bgcolor` : ```str```
                The background color for this node.
            `options` : ``dict``
                A set of options used to control how the node should
                be displayed.

        :Keywords:
          - `show_private_vars`: If false, then private variables
            are filtered out of the attributes & operations lists.
            (Default: *False*)
          - `show_magic_vars`: If false, then magic variables
            (such as ``__init__`` and ``__add__``) are filtered out of
            the attributes & operations lists. (Default: *True*)
          - `show_inherited_vars`: If false, then inherited variables
            are filtered out of the attributes & operations lists.
            (Default: *False*)
          - `max_attributes`: The maximum number of attributes that
            should be listed in the attribute box.  If the class has
            more than this number of attributes, some will be
            ellided.  Ellipsis is marked with ``'...'``.  (Default: 10)
          - `max_operations`: The maximum number of operations that
            should be listed in the operation box. (Default: 5)
          - `add_nodes_for_linked_attributes`: If true, then
            `link_attributes()` will create new a collapsed node for
            the types of a linked attributes if no node yet exists for
            that type.
          - `show_signature_defaults`: If true, then show default
            parameter values in method signatures; if false, then
            hide them.  (Default: *False*)
          - `max_signature_width`: The maximum width (in chars) for
            method signatures.  If the signature is longer than this,
            then it will be trunctated (with ``'...'``).  (Default:
            *60*)
        """
        if not isinstance(class_doc, ClassDoc):
            raise TypeError('Expected a ClassDoc as 1st argument')
        
        self.class_doc = class_doc
        """The class represented by this node."""
        
        self.linker = linker
        """Used to look up URLs for classes."""
        
        self.context = context
        """The context in which the node will be drawn."""
        
        self.bgcolor = bgcolor
        """The background color of the node."""
        
        self.options = options
        """Options used to control how the node is displayed."""

        self.collapsed = collapsed
        """If true, then draw this node as a simple box."""
        
        self.attributes = []
        """The list of VariableDocs for attributes"""
        
        self.operations = []
        """The list of VariableDocs for operations"""
        
        self.qualifiers = []
        """List of (key_label, port) tuples."""

        self.edges = []
        """List of edges used to represent this node's attributes.
        These should not be added to the `DotGraph`; this node will
        generate their dotfile code directly."""

        self.same_rank = []
        """List of nodes that should have the same rank as this one.
        (Used for nodes that are created by _link_attributes)."""

        # Keyword options:
        self._show_signature_defaults = options.get(
            'show_signature_defaults', False)
        self._max_signature_width = options.get(
            'max_signature_width', 60)

        # Initialize operations & attributes lists.
        show_private = options.get('show_private_vars', False)
        show_magic = options.get('show_magic_vars', True)
        show_inherited = options.get('show_inherited_vars', False)
        if class_doc.sorted_variables not in (None, UNKNOWN):
            for var in class_doc.sorted_variables:
                name = var.canonical_name[-1]
                if ((not show_private and var.is_public == False) or
                    (not show_magic and re.match('__\w+__$', name)) or
                    (not show_inherited and var.container != class_doc)):
                    pass
                elif isinstance(var.value, RoutineDoc):
                    self.operations.append(var)
                else:
                    self.attributes.append(var)

        # Initialize our dot node settings.
        tooltip = self._summary(class_doc)
        if tooltip:
            # dot chokes on a \n in the attribute...
            tooltip = " ".join(tooltip.split())
        else:
            tooltip = class_doc.canonical_name
        try: url = linker.url_for(class_doc) or NOOP_URL
        except NotImplementedError: url = NOOP_URL
        DotGraphNode.__init__(self, tooltip=tooltip, width=0, height=0, 
                              shape='plaintext', href=url)

    #/////////////////////////////////////////////////////////////////
    #{ Attribute Linking
    #/////////////////////////////////////////////////////////////////
    
    SIMPLE_TYPE_RE = re.compile(
        r'^([\w\.]+)$')
    """A regular expression that matches descriptions of simple types."""
    
    COLLECTION_TYPE_RE = re.compile(
        r'^(list|set|sequence|tuple|collection) of ([\w\.]+)$')
    """A regular expression that matches descriptions of collection types."""

    MAPPING_TYPE_RE = re.compile(
        r'^(dict|dictionary|map|mapping) from ([\w\.]+) to ([\w\.]+)$')
    """A regular expression that matches descriptions of mapping types."""

    MAPPING_TO_COLLECTION_TYPE_RE = re.compile(
        r'^(dict|dictionary|map|mapping) from ([\w\.]+) to '
        r'(list|set|sequence|tuple|collection) of ([\w\.]+)$')
    """A regular expression that matches descriptions of mapping types
    whose value type is a collection."""

    OPTIONAL_TYPE_RE = re.compile(
        r'^(None or|optional) ([\w\.]+)$|^([\w\.]+) or None$')
    """A regular expression that matches descriptions of optional types."""
    
    def link_attributes(self, graph, nodes):
        """
        Convert any attributes with type descriptions corresponding to
        documented classes to edges.  The following type descriptions
        are currently handled:

          - Dotted names: Create an attribute edge to the named type,
            labelled with the variable name.
          - Collections: Create an attribute edge to the named type,
            labelled with the variable name, and marked with '*' at the
            type end of the edge.
          - Mappings: Create an attribute edge to the named type,
            labelled with the variable name, connected to the class by
            a qualifier box that contains the key type description.
          - Optional: Create an attribute edge to the named type,
            labelled with the variable name, and marked with '0..1' at
            the type end of the edge.

        The edges created by `link_attributes()` are handled internally
        by `DotGraphUmlClassNode`; they should *not* be added directly
        to the `DotGraph`.

        :param nodes: A dictionary mapping from `ClassDoc`\s to
            `DotGraphUmlClassNode`\s, used to look up the nodes for
            attribute types.  If the ``add_nodes_for_linked_attributes``
            option is used, then new nodes will be added to this
            dictionary for any types that are not already listed.
            These added nodes must be added to the `DotGraph`.
        """
        # Try to convert each attribute var into a graph edge.  If
        # _link_attribute returns true, then it succeeded, so remove
        # that var from our attribute list; otherwise, leave that var
        # in our attribute list.
        self.attributes = [var for var in self.attributes
                           if not self._link_attribute(var, graph, nodes)]

    def _link_attribute(self, var, graph, nodes):
        """
        Helper for `link_attributes()`: try to convert the attribute
        variable `var` into an edge, and add that edge to
        `self.edges`.  Return ``True`` iff the variable was
        successfully converted to an edge (in which case, it should be
        removed from the attributes list).
        """
        type_descr = self._type_descr(var) or self._type_descr(var.value)
        
        # Simple type.
        m = self.SIMPLE_TYPE_RE.match(type_descr)
        if m and self._add_attribute_edge(var, graph, nodes, m.group(1)):
            return True

        # Collection type.
        m = self.COLLECTION_TYPE_RE.match(type_descr)
        if m and self._add_attribute_edge(var, graph, nodes, m.group(2),
                                          headlabel='*'):
            return True

        # Optional type.
        m = self.OPTIONAL_TYPE_RE.match(type_descr)
        if m and self._add_attribute_edge(var, graph, nodes,
                                          m.group(2) or m.group(3),
                                          headlabel='0..1'):
            return True
                
        # Mapping type.
        m = self.MAPPING_TYPE_RE.match(type_descr)
        if m:
            port = 'qualifier_%s' % var.name
            if self._add_attribute_edge(var, graph, nodes, m.group(3),
                                        tailport='%s:e' % port):
                self.qualifiers.append( (m.group(2), port) )
                return True

        # Mapping to collection type.
        m = self.MAPPING_TO_COLLECTION_TYPE_RE.match(type_descr)
        if m:
            port = 'qualifier_%s' % var.name
            if self._add_attribute_edge(var, graph, nodes, m.group(4),
                                        headlabel='*', 
                                        tailport='%s:e' % port):
                self.qualifiers.append( (m.group(2), port) )
                return True

        # We were unable to link this attribute.
        return False

    def _add_attribute_edge(self, var, graph, nodes, type_str, **attribs):
        """
        Helper for `link_attributes()`: try to add an edge for the
        given attribute variable `var`.  Return ``True`` if
        successful.
        """
        # Use the type string to look up a corresponding ValueDoc.
        if not hasattr(self.linker, 'docindex'): return False
        type_doc = self.linker.docindex.find(type_str, var)
        if not type_doc: return False

        # Make sure the type is a class.
        if not isinstance(type_doc, ClassDoc): return False

        # Get the type ValueDoc's node.  If it doesn't have one (and
        # add_nodes_for_linked_attributes=True), then create it.
        type_node = nodes.get(type_doc)
        if not type_node:
            if self.options.get('add_nodes_for_linked_attributes', True):
                type_node = DotGraphUmlClassNode(type_doc, self.linker,
                                                 self.context, collapsed=True)
                self.same_rank.append(type_node)
                nodes[type_doc] = type_node
                graph.nodes.append(type_node)
            else:
                return False

        # Add an edge from self to the target type node.
        # [xx] should I set constraint=false here?
        attribs.setdefault('headport', 'body')
        attribs.setdefault('tailport', 'body')
        try: url = self.linker.url_for(var) or NOOP_URL
        except NotImplementedError: url = NOOP_URL
        self.edges.append(DotGraphEdge(self, type_node, label=var.name,
                        arrowtail='odiamond', arrowhead='none', href=url,
                        tooltip=var.canonical_name, labeldistance=1.5,
                        **attribs))
        return True
                           
    #/////////////////////////////////////////////////////////////////
    #{ Helper Methods
    #/////////////////////////////////////////////////////////////////
    def _summary(self, api_doc):
        """Return a plaintext summary for `api_doc`"""
        if not isinstance(api_doc, APIDoc): return ''
        if api_doc.summary in (None, UNKNOWN): return ''
        summary = api_doc.summary.to_plaintext(None).strip()
        return plaintext_to_html(summary)

    _summary = classmethod(_summary)

    def _type_descr(self, api_doc):
        """Return a plaintext type description for `api_doc`"""
        if not hasattr(api_doc, 'type_descr'): return ''
        if api_doc.type_descr in (None, UNKNOWN): return ''
        type_descr = api_doc.type_descr.to_plaintext(self.linker).strip()
        return plaintext_to_html(type_descr)

    def _tooltip(self, var_doc):
        """Return a tooltip for `var_doc`."""
        return (self._summary(var_doc) or
                self._summary(var_doc.value) or
                var_doc.canonical_name)
    
    #/////////////////////////////////////////////////////////////////
    #{ Rendering
    #/////////////////////////////////////////////////////////////////
    
    def _attribute_cell(self, var_doc):
        # Construct the label
        label = var_doc.name
        type_descr = (self._type_descr(var_doc) or
                      self._type_descr(var_doc.value))
        if type_descr: label += ': %s' % type_descr
        # Get the URL
        try: url = self.linker.url_for(var_doc) or NOOP_URL
        except NotImplementedError: url = NOOP_URL
        # Construct & return the pseudo-html code
        return self._ATTRIBUTE_CELL % (url, self._tooltip(var_doc), label)

    def _operation_cell(self, var_doc):
        """
        :todo: do 'word wrapping' on the signature, by starting a new
               row in the table, if necessary.  How to indent the new
               line?  Maybe use align=right?  I don't think dot has a
               &nbsp;.
        :todo: Optionally add return type info?
        """
        # Construct the label (aka function signature)
        func_doc = var_doc.value
        args = [self._operation_arg(n, d, func_doc) for (n, d)
                in zip(func_doc.posargs, func_doc.posarg_defaults)]
        args = [plaintext_to_html(arg) for arg in args]
        if func_doc.vararg: args.append('*'+func_doc.vararg)
        if func_doc.kwarg: args.append('**'+func_doc.kwarg)
        label = '%s(%s)' % (var_doc.name, ', '.join(args))
        if len(label) > self._max_signature_width:
            label = label[:self._max_signature_width-4]+'...)'
        # Get the URL
        try: url = self.linker.url_for(var_doc) or NOOP_URL
        except NotImplementedError: url = NOOP_URL
        # Construct & return the pseudo-html code
        return self._OPERATION_CELL % (url, self._tooltip(var_doc), label)

    def _operation_arg(self, name, default, func_doc):
        """
        :todo: Handle tuple args better
        :todo: Optionally add type info?
        """
        if default is None or not self._show_signature_defaults:
            return '%s' % name
        else:
            pyval_repr = default.summary_pyval_repr().to_plaintext(None)
            return '%s=%s' % (name, pyval_repr)

    def _qualifier_cell(self, key_label, port):
        return self._QUALIFIER_CELL  % (port, self.bgcolor, key_label)

    #: args: (url, tooltip, label)
    _ATTRIBUTE_CELL = '''
    <TR><TD ALIGN="LEFT" HREF="%s" TOOLTIP="%s">%s</TD></TR>
    '''

    #: args: (url, tooltip, label)
    _OPERATION_CELL = '''
    <TR><TD ALIGN="LEFT" HREF="%s" TOOLTIP="%s">%s</TD></TR>
    '''

    #: args: (port, bgcolor, label)
    _QUALIFIER_CELL = '''
    <TR><TD VALIGN="BOTTOM" PORT="%s" BGCOLOR="%s" BORDER="1">%s</TD></TR>
    '''

    _QUALIFIER_DIV = '''
    <TR><TD VALIGN="BOTTOM" HEIGHT="10" WIDTH="10" FIXEDSIZE="TRUE"></TD></TR>
    '''
    
    #: Args: (rowspan, bgcolor, classname, attributes, operations, qualifiers)
    _LABEL = '''
    <TABLE BORDER="0" CELLBORDER="0" CELLSPACING="0" CELLPADDING="0">
      <TR><TD ROWSPAN="%s">
        <TABLE BORDER="0" CELLBORDER="1" CELLSPACING="0" WIDTH="100"
               CELLPADDING="0" PORT="body" BGCOLOR="%s">
          <TR><TD WIDTH="100">%s</TD></TR>
          <TR><TD WIDTH="100"><TABLE BORDER="0" CELLBORDER="0" CELLSPACING="0">
            %s</TABLE></TD></TR>
          <TR><TD WIDTH="100"><TABLE BORDER="0" CELLBORDER="0" CELLSPACING="0">
            %s</TABLE></TD></TR>
        </TABLE>
      </TD></TR>
      %s
    </TABLE>'''

    _COLLAPSED_LABEL = '''
    <TABLE CELLBORDER="0" BGCOLOR="%s" PORT="body">
      <TR><TD WIDTH="50">%s</TD></TR>
    </TABLE>'''

    def _get_html_label(self):
        # Get the class name & contextualize it.
        classname = self.class_doc.canonical_name
        if self.context is not None:
            classname = classname.contextualize(self.context.canonical_name)
        
        # If we're collapsed, display the node as a single box.
        if self.collapsed:
            return self._COLLAPSED_LABEL % (self.bgcolor, classname)
        
        # Construct the attribute list.  (If it's too long, truncate)
        attrib_cells = [self._attribute_cell(a) for a in self.attributes]
        max_attributes = self.options.get('max_attributes', 10)
        if len(attrib_cells) == 0:
            attrib_cells = ['<TR><TD></TD></TR>']
        elif len(attrib_cells) > max_attributes:
            attrib_cells[max_attributes-2:-1] = ['<TR><TD>...</TD></TR>']
        attributes = ''.join(attrib_cells)
                      
        # Construct the operation list.  (If it's too long, truncate)
        oper_cells = [self._operation_cell(a) for a in self.operations]
        max_operations = self.options.get('max_operations', 5)
        if len(oper_cells) == 0:
            oper_cells = ['<TR><TD></TD></TR>']
        elif len(oper_cells) > max_operations:
            oper_cells[max_operations-2:-1] = ['<TR><TD>...</TD></TR>']
        operations = ''.join(oper_cells)

        # Construct the qualifier list & determine the rowspan.
        if self.qualifiers:
            rowspan = len(self.qualifiers)*2+2
            div = self._QUALIFIER_DIV
            qualifiers = div+div.join([self._qualifier_cell(l,p) for
                                     (l,p) in self.qualifiers])+div
        else:
            rowspan = 1
            qualifiers = ''

        # Put it all together.
        return self._LABEL % (rowspan, self.bgcolor, classname,
                              attributes, operations, qualifiers)

    def to_dotfile(self):
        attribs = ['%s="%s"' % (k,v) for (k,v) in self._attribs.items()]
        attribs.append('label=<%s>' % self._get_html_label())
        s = 'node%d%s' % (self.id, ' [%s]' % (','.join(attribs)))
        if not self.collapsed:
            for edge in self.edges:
                s += '\n' + edge.to_dotfile()
        if self.same_rank:
            sr_nodes = ''.join(['node%s; ' % node.id
                                for node in self.same_rank])
            # [xx] This can cause dot to crash!  not sure why!
            #s += '{rank=same; node%s; %s}' % (self.id, sr_nodes)
        return s

class DotGraphUmlModuleNode(DotGraphNode):
    """
    A specialized dot grah node used to display `ModuleDoc`\s using
    UML notation.  Simple module nodes look like::

        .----.
        +------------+
        | modulename |
        +------------+

    Packages nodes are drawn with their modules & subpackages nested
    inside::
        
        .----.
        +----------------------------------------+
        | packagename                            |
        |                                        |
        |  .----.       .----.       .----.      |
        |  +---------+  +---------+  +---------+ |
        |  | module1 |  | module2 |  | module3 | |
        |  +---------+  +---------+  +---------+ |
        |                                        |
        +----------------------------------------+

    """
    def __init__(self, module_doc, linker, context, collapsed=False,
                 excluded_submodules=(), **options):
        self.module_doc = module_doc
        self.linker = linker
        self.context = context
        self.collapsed = collapsed
        self.options = options
        self.excluded_submodules = excluded_submodules
        try: url = linker.url_for(module_doc) or NOOP_URL
        except NotImplementedError: url = NOOP_URL
        DotGraphNode.__init__(self, shape='plaintext', href=url,
                              tooltip=module_doc.canonical_name)

    #: Expects: (color, color, url, tooltip, body)
    _MODULE_LABEL = ''' 
    <TABLE BORDER="0" CELLBORDER="0" CELLSPACING="0" ALIGN="LEFT">
    <TR><TD ALIGN="LEFT" VALIGN="BOTTOM" HEIGHT="8" WIDTH="16"
            FIXEDSIZE="true" BGCOLOR="%s" BORDER="1" PORT="tab"></TD></TR>
    <TR><TD ALIGN="LEFT" VALIGN="TOP" BGCOLOR="%s" BORDER="1" WIDTH="20"
            PORT="body" HREF="%s" TOOLTIP="%s">%s</TD></TR>
    </TABLE>'''

    #: Expects: (name, body_rows)
    _NESTED_BODY = '''
    <TABLE BORDER="0" CELLBORDER="0" CELLPADDING="0" CELLSPACING="0">
    <TR><TD ALIGN="LEFT">%s</TD></TR>
    %s
    </TABLE>'''

    #: Expects: (cells,)
    _NESTED_BODY_ROW = '''
    <TR><TD>
      <TABLE BORDER="0" CELLBORDER="0"><TR>%s</TR></TABLE>
    </TD></TR>'''
    
    def _get_html_label(self, package):
        """
        :Return: (label, depth, width) where:
        
          - ``label`` is the HTML label
          - ``depth`` is the depth of the package tree (for coloring)
          - ``width`` is the max width of the HTML label, roughly in
            units of characters.
        """
        MAX_ROW_WIDTH = 80 # unit is roughly characters.
        pkg_name = package.canonical_name
        try: pkg_url = self.linker.url_for(package) or NOOP_URL
        except NotImplementedError: pkg_url = NOOP_URL
        
        if (not package.is_package or len(package.submodules) == 0 or
            self.collapsed):
            pkg_color = self._color(package, 1)
            label = self._MODULE_LABEL % (pkg_color, pkg_color,
                                          pkg_url, pkg_name, pkg_name[-1])
            return (label, 1, len(pkg_name[-1])+3)
                
        # Get the label for each submodule, and divide them into rows.
        row_list = ['']
        row_width = 0
        max_depth = 0
        max_row_width = len(pkg_name[-1])+3
        for submodule in package.submodules:
            if submodule in self.excluded_submodules: continue
            # Get the submodule's label.
            label, depth, width = self._get_html_label(submodule)
            # Check if we should start a new row.
            if row_width > 0 and width+row_width > MAX_ROW_WIDTH:
                row_list.append('')
                row_width = 0
            # Add the submodule's label to the row.
            row_width += width
            row_list[-1] += '<TD ALIGN="LEFT">%s</TD>' % label
            # Update our max's.
            max_depth = max(depth, max_depth)
            max_row_width = max(row_width, max_row_width)

        # Figure out which color to use.
        pkg_color = self._color(package, depth+1)
        
        # Assemble & return the label.
        rows = ''.join([self._NESTED_BODY_ROW % r for r in row_list])
        body = self._NESTED_BODY % (pkg_name, rows)
        label = self._MODULE_LABEL % (pkg_color, pkg_color,
                                      pkg_url, pkg_name, body)
        return label, max_depth+1, max_row_width

    _COLOR_DIFF = 24
    def _color(self, package, depth):
        if package == self.context: return COLOR['SELECTED_BG']
        else: 
            # Parse the base color.
            if re.match(COLOR['MODULE_BG'], 'r#[0-9a-fA-F]{6}$'):
                base = int(COLOR['MODULE_BG'][1:], 16)
            else:
                base = int('d8e8ff', 16)
            red = (base & 0xff0000) >> 16
            green = (base & 0x00ff00) >> 8
            blue = (base & 0x0000ff)
            # Make it darker with each level of depth. (but not *too*
            # dark -- package name needs to be readable)
            red = max(64, red-(depth-1)*self._COLOR_DIFF)
            green = max(64, green-(depth-1)*self._COLOR_DIFF)
            blue = max(64, blue-(depth-1)*self._COLOR_DIFF)
            # Convert it back to a color string
            return '#%06x' % ((red<<16)+(green<<8)+blue)
        
    def to_dotfile(self):
        attribs = ['%s="%s"' % (k,v) for (k,v) in self._attribs.items()]
        label, depth, width = self._get_html_label(self.module_doc)
        attribs.append('label=<%s>' % label)
        return 'node%d%s' % (self.id, ' [%s]' % (','.join(attribs)))


    
######################################################################
#{ Graph Generation Functions
######################################################################

def package_tree_graph(packages, linker, context=None, **options):
    """
    Return a `DotGraph` that graphically displays the package
    hierarchies for the given packages.
    """
    if options.get('style', 'uml') == 'uml': # default to uml style?
        if get_dot_version() >= [2]:
            return uml_package_tree_graph(packages, linker, context,
                                             **options)
        elif 'style' in options:
            log.warning('UML style package trees require dot version 2.0+')

    graph = DotGraph('Package Tree for %s' % name_list(packages, context),
                     body='ranksep=.3\nnodesep=.1\n',
                     edge_defaults={'dir':'none'})
    
    # Options
    if options.get('dir', 'TB') != 'TB': # default: top-to-bottom
        graph.body += 'rankdir=%s\n' % options.get('dir', 'TB')

    # Get a list of all modules in the package.
    queue = list(packages)
    modules = set(packages)
    for module in queue:
        queue.extend(module.submodules)
        modules.update(module.submodules)

    # Add a node for each module.
    nodes = add_valdoc_nodes(graph, modules, linker, context)

    # Add an edge for each package/submodule relationship.
    for module in modules:
        for submodule in module.submodules:
            graph.edges.append(DotGraphEdge(nodes[module], nodes[submodule],
                                            headport='tab'))

    return graph

def uml_package_tree_graph(packages, linker, context=None, **options):
    """
    Return a `DotGraph` that graphically displays the package
    hierarchies for the given packages as a nested set of UML
    symbols.
    """
    graph = DotGraph('Package Tree for %s' % name_list(packages, context))
    # Remove any packages whose containers are also in the list.
    root_packages = []
    for package1 in packages:
        for package2 in packages:
            if (package1 is not package2 and
                package2.canonical_name.dominates(package1.canonical_name)):
                break
        else:
            root_packages.append(package1)
    # If the context is a variable, then get its value.
    if isinstance(context, VariableDoc) and context.value is not UNKNOWN:
        context = context.value
    # Return a graph with one node for each root package.
    for package in root_packages:
        graph.nodes.append(DotGraphUmlModuleNode(package, linker, context))
    return graph

######################################################################
def class_tree_graph(classes, linker, context=None, **options):
    """
    Return a `DotGraph` that graphically displays the class
    hierarchy for the given classes.  Options:

      - exclude: A list of classes that should be excluded
      - dir: LR|RL|BT requests a left-to-right, right-to-left, or
        bottom-to- top, drawing.  (corresponds to the dot option
        'rankdir'
      - max_subclass_depth: The maximum depth to which subclasses
        will be drawn.
      - max_subclasses: A list of ints, specifying how many
        subclasses should be drawn per class at each level of the
        graph.  E.g., [5,3,1] means draw up to 5 subclasses for the
        specified classes; up to 3 subsubclasses for each of those (up
        to) 5 subclasses; and up to 1 subclass for each of those.
    """
    # Callbacks:
    def mknode(cls, nodetype, linker, context, options):
        return mk_valdoc_node(cls, linker, context)
    def mkedge(start, end, edgetype, options):
        return DotGraphEdge(start, end)
    
    if isinstance(classes, ClassDoc): classes = [classes]
    # [xx] this should be done earlier, and should generate a warning:
    classes = [c for c in classes if c is not None] 
    graph = DotGraph('Class Hierarchy for %s' % name_list(classes, context),
                     body='ranksep=0.3\n',
                     edge_defaults={'sametail':True, 'dir':'none'})
    _class_tree_graph(graph, classes, mknode, mkedge, linker, 
                      context, options, cls2node={})
    return graph

def _class_tree_graph(graph, classes, mknode, mkedge, linker,
                      context, options, cls2node):
    """
    A helper function that is used by both `class_tree_graph()` and
    `uml_class_tree_graph()` to draw class trees.  To abstract over
    the differences between the two, this function takes two callback
    functions that create graph nodes and edges:

    - ``mknode(base, nodetype, linker, context, options)``: Returns
      a `DotGraphNode`.  ``nodetype`` is one of: subclass, superclass,
      selected, undocumented.
    - ``mkedge(begin, end, edgetype, options)``: Returns a
      `DotGraphEdge`.  ``edgetype`` is one of: subclass,
      truncate-subclass.
    """
    rankdir = options.get('dir', 'TB')
    graph.body += 'rankdir=%s\n' % rankdir
    truncated = set()  # Classes whose subclasses were truncated
    _add_class_tree_superclasses(graph, classes, mknode, mkedge, linker,
                                 context, options, cls2node)
    _add_class_tree_subclasses(graph, classes, mknode, mkedge, linker,
                               context, options, cls2node, truncated)
    _add_class_tree_inheritance(graph, classes, mknode, mkedge, linker,
                                context, options, cls2node, truncated)

def _add_class_tree_superclasses(graph, classes, mknode, mkedge, linker,
                                 context, options, cls2node):
    exclude = options.get('exclude', ())
    
    # Create nodes for all bases.
    for cls in classes:
        for base in cls.mro():
            # Don't include 'object'
            if base.canonical_name == DottedName('object'): continue
            # Stop if we reach an excluded class.
            if base in exclude: break
            # Don't do the same class twice.
            if base in cls2node: continue
            # Decide if the base is documented.
            try: documented = (linker.url_for(base) is not None)
            except: documented = True
            # Make the node.
            if base in classes: typ = 'selected'
            elif not documented: typ = 'undocumented'
            else: typ = 'superclass'
            cls2node[base] = mknode(base, typ, linker, context, options)
            graph.nodes.append(cls2node[base])

def _add_class_tree_subclasses(graph, classes, mknode, mkedge, linker,
                               context, options, cls2node, truncated):
    exclude = options.get('exclude', ())
    max_subclass_depth = options.get('max_subclass_depth', 3)
    max_subclasses = list(options.get('max_subclasses', (5,3,2,1)))
    max_subclasses += len(classes)*max_subclasses[-1:] # repeat last num
    
    # Find the depth of each subclass (for truncation)
    subclass_depth = _get_subclass_depth_map(classes)

    queue = list(classes)
    for cls in queue:
        # If there are no subclasses, then we're done.
        if not isinstance(cls, ClassDoc): continue
        if cls.subclasses in (None, UNKNOWN, (), []): continue
        # Get the list of subclasses.
        subclasses = [subcls for subcls in cls.subclasses
                      if subcls not in cls2node and subcls not in exclude]
        # If the subclass list is too long, then truncate it.
        if len(subclasses) > max_subclasses[subclass_depth[cls]]:
            subclasses = subclasses[:max_subclasses[subclass_depth[cls]]]
            truncated.add(cls)
        # Truncate any classes that are too deep.
        num_subclasses = len(subclasses)
        subclasses = [subcls for subcls in subclasses
                      if subclass_depth[subcls] <= max_subclass_depth]
        if len(subclasses) < num_subclasses: truncated.add(cls)
        # Add a node for each subclass.
        for subcls in subclasses:
            cls2node[subcls] = mknode(subcls, 'subclass', linker,
                                      context, options)
            graph.nodes.append(cls2node[subcls])
        # Add the subclasses to our queue.
        queue.extend(subclasses)

def _add_class_tree_inheritance(graph, classes, mknode, mkedge, linker,
                                context, options, cls2node, truncated):
    # Add inheritance edges.
    for (cls, node) in cls2node.items():
        if cls.bases is UNKNOWN: continue
        for base in cls.bases:
            if base in cls2node:
                graph.edges.append(mkedge(cls2node[base], node,
                                          'subclass', options))
    # Mark truncated classes
    for cls in truncated:
        ellipsis = DotGraphNode('...', shape='plaintext',
                                width='0', height='0')
        graph.nodes.append(ellipsis)
        graph.edges.append(mkedge(cls2node[cls], ellipsis,
                                  'truncate-subclass', options))

def _get_subclass_depth_map(classes):
    subclass_depth = dict([(cls,0) for cls in classes])
    queue = list(classes)
    for cls in queue:
        if (isinstance(cls, ClassDoc) and
            cls.subclasses not in (None, UNKNOWN)):
            for subcls in cls.subclasses:
                subclass_depth[subcls] = max(subclass_depth.get(subcls,0),
                                             subclass_depth[cls]+1)
                queue.append(subcls)
    return subclass_depth

    

######################################################################
def uml_class_tree_graph(classes, linker, context=None, **options):
    """
    Return a `DotGraph` that graphically displays the class hierarchy
    for the given class, using UML notation.  Options:

      - exclude: A list of classes that should be excluded
      - dir: LR|RL|BT requests a left-to-right, right-to-left, or
        bottom-to- top, drawing.  (corresponds to the dot option
        'rankdir'
      - max_subclass_depth: The maximum depth to which subclasses
        will be drawn.
      - max_subclasses: A list of ints, specifying how many
        subclasses should be drawn per class at each level of the
        graph.  E.g., [5,3,1] means draw up to 5 subclasses for the
        specified classes; up to 3 subsubclasses for each of those (up
        to) 5 subclasses; and up to 1 subclass for each of those.
      - max_attributes
      - max_operations
      - show_private_vars
      - show_magic_vars
      - link_attributes
      - show_signature_defaults
      - max_signature_width
    """
    cls2node = {}

    # Draw the basic graph:
    if isinstance(classes, ClassDoc): classes = [classes]
    graph = DotGraph('UML class diagram for %s' % name_list(classes, context),
                     body='ranksep=.2\n;nodesep=.3\n')
    _class_tree_graph(graph, classes, _uml_mknode, _uml_mkedge,
                      linker, context, options, cls2node)

    # Turn attributes into links (optional):
    inheritance_nodes = set(graph.nodes)
    if options.get('link_attributes', True):
        for cls in classes:
            for base in cls.mro():
                node = cls2node.get(base)
                if node is None: continue
                node.link_attributes(graph, cls2node)
                # Make sure that none of the new attribute edges break
                # the rank ordering assigned by inheritance.
                for edge in node.edges:
                    if edge.end in inheritance_nodes:
                        edge['constraint'] = 'False'

    return graph

# A callback to make graph nodes:
def _uml_mknode(cls, nodetype, linker, context, options):
    if nodetype == 'subclass':
        return DotGraphUmlClassNode(
            cls, linker, context, collapsed=True,
            bgcolor=COLOR['SUBCLASS_BG'], **options)
    elif nodetype in ('selected', 'superclass', 'undocumented'):
        if nodetype == 'selected': bgcolor = COLOR['SELECTED_BG']
        if nodetype == 'superclass': bgcolor = COLOR['BASECLASS_BG']
        if nodetype == 'undocumented': bgcolor = COLOR['UNDOCUMENTED_BG']
        return DotGraphUmlClassNode(
            cls, linker, context, show_inherited_vars=False,
            collapsed=False, bgcolor=bgcolor, **options)
    assert 0, 'bad nodetype'    

# A callback to make graph edges:
def _uml_mkedge(start, end, edgetype, options):
    if edgetype == 'subclass':
        return DotGraphEdge(
            start, end, dir='back', arrowtail='empty',
            headport='body', tailport='body', color=COLOR['INH_LINK'],
            weight=100, style='bold')
    if edgetype == 'truncate-subclass':
        return DotGraphEdge(
            start, end, dir='back', arrowtail='empty',
            tailport='body', color=COLOR['INH_LINK'],
            weight=100, style='bold')
    assert 0, 'bad edgetype'    

######################################################################
def import_graph(modules, docindex, linker, context=None, **options):
    graph = DotGraph('Import Graph', body='ranksep=.3\n;nodesep=.3\n')

    # Options
    if options.get('dir', 'RL') != 'TB': # default: right-to-left.
        graph.body += 'rankdir=%s\n' % options.get('dir', 'RL')

    # Add a node for each module.
    nodes = add_valdoc_nodes(graph, modules, linker, context)

    # Edges.
    edges = set()
    for dst in modules:
        if dst.imports in (None, UNKNOWN): continue
        for var_name in dst.imports:
            for i in range(len(var_name), 0, -1):
                val_doc = docindex.find(var_name[:i], context)
                if isinstance(val_doc, ModuleDoc):
                    if val_doc in nodes and dst in nodes:
                        edges.add((nodes[val_doc], nodes[dst]))
                    break
    graph.edges = [DotGraphEdge(src,dst) for (src,dst) in edges]

    return graph

######################################################################
def call_graph(api_docs, docindex, linker, context=None, **options):
    """
    :param options:
        - ``dir``: rankdir for the graph.  (default=LR)
        - ``add_callers``: also include callers for any of the
          routines in ``api_docs``.  (default=False)
        - ``add_callees``: also include callees for any of the
          routines in ``api_docs``.  (default=False)
    :todo: Add an ``exclude`` option?
    """
    if docindex.callers is None:
        log.warning("No profiling information for call graph!")
        return DotGraph('Call Graph') # return None instead?

    if isinstance(context, VariableDoc):
        context = context.value

    # Get the set of requested functions.
    functions = []
    for api_doc in api_docs:
        # If it's a variable, get its value.
        if isinstance(api_doc, VariableDoc):
            api_doc = api_doc.value
        # Add the value to the functions list.
        if isinstance(api_doc, RoutineDoc):
            functions.append(api_doc)
        elif isinstance(api_doc, NamespaceDoc):
            for vardoc in api_doc.variables.values():
                if isinstance(vardoc.value, RoutineDoc):
                    functions.append(vardoc.value)

    # Filter out functions with no callers/callees?
    # [xx] this isnt' quite right, esp if add_callers or add_callees
    # options are fales.
    functions = [f for f in functions if
                 (f in docindex.callers) or (f in docindex.callees)]
        
    # Add any callers/callees of the selected functions
    func_set = set(functions)
    if options.get('add_callers', False) or options.get('add_callees', False):
        for func_doc in functions:
            if options.get('add_callers', False):
                func_set.update(docindex.callers.get(func_doc, ()))
            if options.get('add_callees', False):
                func_set.update(docindex.callees.get(func_doc, ()))

    graph = DotGraph('Call Graph for %s' % name_list(api_docs, context),
                     node_defaults={'shape':'box', 'width': 0, 'height': 0})
    
    # Options
    if options.get('dir', 'LR') != 'TB': # default: left-to-right
        graph.body += 'rankdir=%s\n' % options.get('dir', 'LR')

    nodes = add_valdoc_nodes(graph, func_set, linker, context)
    
    # Find the edges.
    edges = set()
    for func_doc in functions:
        for caller in docindex.callers.get(func_doc, ()):
            if caller in nodes:
                edges.add( (nodes[caller], nodes[func_doc]) )
        for callee in docindex.callees.get(func_doc, ()):
            if callee in nodes:
                edges.add( (nodes[func_doc], nodes[callee]) )
    graph.edges = [DotGraphEdge(src,dst) for (src,dst) in edges]
    
    return graph

######################################################################
#{ Dot Version
######################################################################

_dot_version = None
_DOT_VERSION_RE = re.compile(r'dot version ([\d\.]+)')
def get_dot_version():
    global _dot_version
    if _dot_version is None:
        try:
            out, err = run_subprocess([DOT_COMMAND, '-V'])
            version_info = err or out
            m = _DOT_VERSION_RE.match(version_info)
            if m:
                _dot_version = [int(x) for x in m.group(1).split('.')]
            else:
                _dot_version = (0,)
        except OSError, e:
            log.error('dot executable not found; graphs will not be '
                      'generated.  Adjust your shell\'s path, or use '
                      '--dotpath to specify the path to the dot '
                      'executable.' % DOT_COMMAND)
            _dot_version = (0,)
        log.info('Detected dot version %s' % _dot_version)
    return _dot_version

######################################################################
#{ Helper Functions
######################################################################

def add_valdoc_nodes(graph, val_docs, linker, context):
    """
    :todo: Use different node styles for different subclasses of APIDoc
    """
    nodes = {}
    for val_doc in sorted(val_docs, key=lambda d:d.canonical_name):
        nodes[val_doc] = mk_valdoc_node(val_doc, linker, context)
        graph.nodes.append(nodes[val_doc])
    return nodes

def mk_valdoc_node(val_doc, linker, context):
    label = val_doc.canonical_name
    if context is not None:
        label = label.contextualize(context.canonical_name)
    node = DotGraphNode(label)
    specialize_valdoc_node(node, val_doc, context, linker)
    return node

NOOP_URL = 'javascript:void(0);'
MODULE_NODE_HTML = '''
  <TABLE BORDER="0" CELLBORDER="0" CELLSPACING="0"
         CELLPADDING="0" PORT="table" ALIGN="LEFT">
  <TR><TD ALIGN="LEFT" VALIGN="BOTTOM" HEIGHT="8" WIDTH="16" FIXEDSIZE="true"
          BGCOLOR="%s" BORDER="1" PORT="tab"></TD></TR>
  <TR><TD ALIGN="LEFT" VALIGN="TOP" BGCOLOR="%s" BORDER="1"
          PORT="body" HREF="%s" TOOLTIP="%s">%s</TD></TR>
  </TABLE>'''.strip()

def specialize_valdoc_node(node, val_doc, context, linker):
    """
    Update the style attributes of `node` to reflext its type
    and context.
    """
    # We can only use html-style nodes if dot_version>2.
    dot_version = get_dot_version()
    
    # If val_doc or context is a variable, get its value.
    if isinstance(val_doc, VariableDoc) and val_doc.value is not UNKNOWN:
        val_doc = val_doc.value
    if isinstance(context, VariableDoc) and context.value is not UNKNOWN:
        context = context.value

    # Set the URL.  (Do this even if it points to the page we're
    # currently on; otherwise, the tooltip is ignored.)
    try: url = linker.url_for(val_doc) or NOOP_URL
    except NotImplementedError: url = NOOP_URL
    node['href'] = url

    if (url is None and
        hasattr(linker, 'docindex') and
        linker.docindex.find(identifier, self.container) is None):
        node['fillcolor'] = COLOR['UNDOCUMENTED_BG']
        node['style'] = 'filled'

    if isinstance(val_doc, ModuleDoc) and dot_version >= [2]:
        node['shape'] = 'plaintext'
        if val_doc == context: color = COLOR['SELECTED_BG']
        else: color = COLOR['MODULE_BG']
        node['tooltip'] = node['label']
        node['html_label'] = MODULE_NODE_HTML % (color, color, url,
                                                 val_doc.canonical_name,
                                                 node['label'])
        node['width'] = node['height'] = 0
        node.port = 'body'

    elif isinstance(val_doc, RoutineDoc):
        node['shape'] = 'box'
        node['style'] = 'rounded'
        node['width'] = 0
        node['height'] = 0
        node['label'] = '%s()' % node['label']
        node['tooltip'] = node['label']
        if val_doc == context:
            node['fillcolor'] = COLOR['SELECTED_BG']
            node['style'] = 'filled,rounded,bold'
            
    else:
        node['shape'] = 'box' 
        node['width'] = 0
        node['height'] = 0
        node['tooltip'] = node['label']
        if val_doc == context:
            node['fillcolor'] = COLOR['SELECTED_BG']
            node['style'] = 'filled,bold'

def name_list(api_docs, context=None):
    names = [d.canonical_name for d in api_docs]
    if context is not None:
        names = [name.contextualize(context.canonical_name) for name in names]
    if len(names) == 0: return ''
    if len(names) == 1: return '%s' % names[0]
    elif len(names) == 2: return '%s and %s' % (names[0], names[1])
    else:
        names = ['%s' % name for name in names]
        return '%s, and %s' % (', '.join(names[:-1]), names[-1])

