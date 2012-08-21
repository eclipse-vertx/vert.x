# -*- latex -*-
#
# epydoc.css: LaTeX stylesheets (*.sty) for epydoc's LaTeX writer
#
# $Id: html_css.py 1717 2008-02-14 22:21:41Z edloper $
#

"""
LaTeX stylesheets (*.sty) for epydoc's LaTeX writer.
"""

#: A disclaimer that is appended to the bottom of the BASE and
#: BOXES stylesheets.
NIST_DISCLAIMER = r"""
% This style file is a derivative work, based on a public domain style
% file that was originally developed at the National Institute of
% Standards and Technology by employees of the Federal Government in the
% course of their official duties.  NIST assumes no responsibility
% whatsoever for its use by other parties, and makes no guarantees,
% expressed or implied, about its quality, reliability, or any other
% characteristic.
"""

######################################################################
######################################################################
BASE = r"""
% epydoc-base.sty
%
% Authors: Jonathan Guyer <guyer@nist.gov>
%          Edward Loper <edloper@seas.upenn.edu>
% URL: <http://epydoc.sf.net>
%
% This LaTeX stylesheet defines the basic commands that are used by
% epydoc's latex writer (epydoc.docwriter.latex).  For more
% information, see the epydoc webpage.  This stylesheet accepts the
% following options:
%
%   - index: Include an index of defined objects.
%   - hyperlink: Create hyperlinks with the hyperref package.
%
% $Id:$

\NeedsTeXFormat{LaTeX2e}%
\ProvidesClass{epydoc-base}[2008/02/26 v3.0.1 Epydoc Python Documentation]

% ======================================================================
% Options

% These two packages are used to process options:
\RequirePackage{ifthen}
\RequirePackage{xkeyval}

% Define an option 'index' that sets the boolean value \@doIndex
\newif\if@doIndex
\@doIndexfalse
\DeclareOptionX{index}{\@doIndextrue}

% Define an option 'hyperlink' that sets the boolean value \@docHyperlink
\newif\if@doHyperlink
\@doHyperlinkfalse
\DeclareOptionX{hyperlink}{\@doHyperlinktrue}

% Pass the 'title' & 'creator' options to the hyperref package.
\DeclareOptionX{title}[]{\PassOptionsToPackage{pdftitle={#1}}{hyperref}}
\DeclareOptionX{creator}[]{\PassOptionsToPackage{pdfcreator={#1}}{hyperref}}

% Process the options list.
\ProcessOptionsX\relax

% ======================================================================
% Package Requirements

\RequirePackage{alltt, boxedminipage}
\RequirePackage{multirow, amssymb}
\RequirePackage[headings]{fullpage}
\RequirePackage[usenames]{color}
\RequirePackage{graphicx}

\@ifclassloaded{memoir}{%
    \RequirePackage[other,notbib]{tocbibind}
}{%
    \if@doIndex
        \RequirePackage{makeidx}
    \fi
    \RequirePackage{parskip}
    \RequirePackage{fancyhdr}
    \RequirePackage[other]{tocbibind}
    \pagestyle{fancy}
}

\if@doIndex
    \makeindex
\fi

\ifx\pdfoutput\undefined\newcommand{\driver}{dvips}
\else\ifnum\pdfoutput=1\newcommand{\driver}{pdftex}
\else\newcommand{\driver}{dvips}\fi\fi

\RequirePackage[\driver, pagebackref, 
    bookmarks=true, bookmarksopen=false, pdfpagemode=UseOutlines, 
    colorlinks=true, linkcolor=black, anchorcolor=black, citecolor=black, 
    filecolor=black, menucolor=black, pagecolor=black, urlcolor=UrlColor]
    {hyperref}


% ======================================================================
% General Formatting

% Setting this will make us less likely to get overfull hboxes:
%\setlength{\emergencystretch}{1in}

% Separate paragraphs by a blank line (do not indent them).  We define
% a new length variable. \EpydocParskip, for the paragraph-skip length.
% This needs to be assigned to \parskip here as well as inside several
% environments that reset \parskip (such as minipages).
\newlength{\EpydocParskip}
\newlength{\EpydocMetadataLongListParskip}
\setlength{\EpydocParskip}{0.6\baselineskip}
\setlength{\EpydocMetadataLongListParskip}{0.4\baselineskip}
\setlength{\parskip}{\EpydocParskip}
\setlength{\parindent}{0ex}

% Fix the heading position -- without this, the headings generated
% by the fancyheadings package sometimes overlap the text.
\setlength{\headheight}{16pt}
\setlength{\headsep}{24pt}
\setlength{\topmargin}{-\headsep}

% Display the section & subsection names in a header.
\renewcommand{\sectionmark}[1]{\markboth{#1}{}}
\renewcommand{\subsectionmark}[1]{\markright{#1}}

% Create a 'base class' length named EpydocBCL for use in base trees.
\newlength{\EpydocBCL} % base class length, for base trees.

% In xkeyval arguments, we're not allowed to use \par.  But we *are*
% allowed to use a command that evaluates to \par, so define such a
% command.
\newcommand{\EpydocPar}{\par}

% ======================================================================
% Sections inside docstring

% The following commands are used to mark section headers within
% docstrings.
\newcommand\EpydocUserSection[1]{%
  \par\vspace{3ex}{\large\bf #1 }\par\vspace{1.4ex}}
\newcommand\EpydocUserSubsection[1]{%
  \par\vspace{2.8ex}{\bf #1 }\par\vspace{1.2ex}}
\newcommand\EpydocUserSubsubsection[1]{%
  \par\vspace{2.6ex}{\bf\it #1 }\par\vspace{1.0ex}}

% ======================================================================
% Hyperlinks & Crossreferences

% The \EpydocHypertarget command is used to mark targets that hyperlinks
% may point to.  It takes two arguments: a target label, and text
% contents.  (In some cases, the text contents will be empty.)  Target
% labels are formed by replacing '.'s in the name with ':'s.  The
% default stylesheet creates a \label for the target label, and displays
% the text.
\newcommand{\EpydocHypertarget}[2]{\label{#1}#2}

% The \EpydocHyperlink command is used to create a link to a given target.
% It takes two arguments: a target label, and text contents.  The
% default stylesheet just displays the text contents.
\newcommand{\EpydocHyperlink}[2]{#2}

% The \CrossRef command creates a cross-reference to a given target,
% including a pageref.  It takes one argument, a target label.
\newcommand{\CrossRef}[1]{\textit{(Section \ref{#1}, p.~\pageref{#1})}}

% If the [hyperlink] option is turned on, then enable hyperlinking.
\if@doHyperlink
  \renewcommand{\EpydocHyperlink}[2]{\hyperlink{#1}{#2}}
  \renewcommand{\EpydocHypertarget}[2]{\label{#1}\hypertarget{#1}{#2}}
\fi

% ======================================================================
% Index Terms

% The \EpydocIndex command is used to mark items that should be included
% in the index.  It takes three arguments.  The first argument is the
% item's case-normalized name; this is typically discarded, and is
% simply used to ensure the proper (i.e., case-insensitive) sort order
% in the index.  The second argument is the item's name; and the
% third item is the item's "kind".  "kind" can be Package, Script, Module,
% Class, Class Method, Static Method, Method, Function, or Variable.
% This command is used inside of the \index{...} command.
\newcommand{\EpydocIndex}[3]{%
    #2 %
    \ifthenelse{\equal{#3}{}}{}{\textit{(#3)}}}
    
% ======================================================================
% Descriptions (docstring contents)

% All rendered markup derived from a docstring is wrapped in this
% environment.  By default, it simply sets the \parskip length
% to \EpydocParskip (in case an enclosing environment had reset
% it to its default value).
\newenvironment{EpydocDescription}{%
    \setlength{\parskip}{\EpydocParskip}%
    \ignorespaces}{\ignorespacesafterend}

% This environment is used to mark the description for a class
% (which comes from the function's docstring).
\newenvironment{EpydocClassDescription}{%
    \ignorespaces}{\ignorespacesafterend}

% This environment is used to mark the description for a module
% (which comes from the function's docstring).
\newenvironment{EpydocModuleDescription}{%
    \ignorespaces}{\ignorespacesafterend}

% ======================================================================
% Python Source Code Syntax Highlighting.

% Color constants.
\definecolor{py@keywordcolor}{rgb}{1,0.45882,0}
\definecolor{py@stringcolor}{rgb}{0,0.666666,0}
\definecolor{py@commentcolor}{rgb}{1,0,0}
\definecolor{py@ps1color}{rgb}{0.60784,0,0}
\definecolor{py@ps2color}{rgb}{0.60784,0,1}
\definecolor{py@inputcolor}{rgb}{0,0,0}
\definecolor{py@outputcolor}{rgb}{0,0,1}
\definecolor{py@exceptcolor}{rgb}{1,0,0}
\definecolor{py@defnamecolor}{rgb}{1,0.5,0.5}
\definecolor{py@builtincolor}{rgb}{0.58039,0,0.58039}
\definecolor{py@identifiercolor}{rgb}{0,0,0}
\definecolor{py@linenumcolor}{rgb}{0.4,0.4,0.4}
\definecolor{py@inputcolor}{rgb}{0,0,0}

% Syntax Highlighting Commands
\newcommand{\pysrcprompt}[1]{\textcolor{py@ps1color}{\small\textbf{#1}}}
\newcommand{\pysrcmore}[1]{\textcolor{py@ps2color}{\small\textbf{#1}}}
\newcommand{\pysrckeyword}[1]{\textcolor{py@keywordcolor}{\small\textbf{#1}}}
\newcommand{\pysrcbuiltin}[1]{\textcolor{py@builtincolor}{\small\textbf{#1}}}
\newcommand{\pysrcstring}[1]{\textcolor{py@stringcolor}{\small\textbf{#1}}}
\newcommand{\pysrcdefname}[1]{\textcolor{py@defnamecolor}{\small\textbf{#1}}}
\newcommand{\pysrcother}[1]{\small\textbf{#1}}
\newcommand{\pysrccomment}[1]{\textcolor{py@commentcolor}{\small\textbf{#1}}}
\newcommand{\pysrcoutput}[1]{\textcolor{py@outputcolor}{\small\textbf{#1}}}
\newcommand{\pysrcexcept}[1]{\textcolor{py@exceptcolor}{\small\textbf{#1}}}

% ======================================================================
% Grouping

% This command is used to display group headers for objects that are in
% the same group (as specified by the epydoc @group field).  It is used
% within the Epydoc*List environments.  The definition provided here is
% the default definition, but several of the Epydoc*List environments
% use \renewcommand to provide definitions that are appropriate for the
% style of that environment.
\newcommand{\EpydocGroup}[1]{\par{\large #1}\par}

% ======================================================================
% Inheritance

% This command is used to display a list of objects that were inherited
% from a base class.  It expects two arguments: the base class name,
% and the list of inherited objects.  The definition provided here is
% the default definition, but several of the Epydoc*List environments
% use \renewcommand to provide definitions that are appropriate for the
% style of that environment.
\newcommand{\EpydocInheritanceList}[2]{%
    \textbf{Inherited from {#1}:} #2\par}
    
% ======================================================================
% Submodule List

% This list environment is used to list the submodules that are defined
% by a module.  Nested submodules are displayed using nested
% EpydocModuleList environments.  If the modules are divided into
% groups (with the epydoc @group field), then groups are displayed
% using the \EpydocModuleGroup command, followed by a nested
% EpydocModuleList.
\newenvironment{EpydocModuleList}{%
    \renewcommand{\EpydocGroup}[1]{\item[##1] \
    }
    \begin{itemize}
        \renewcommand{\makelabel}[1]{\textbf{##1}:} %
    \setlength{\parskip}{0ex}%
    }
    {\end{itemize}}

% ======================================================================
% Class List
%
% These environments are *only* used if the --list-classes-separately
% option is used.

% This list environment is used to list the classes that are defined 
% by a module.
\newenvironment{EpydocClassList}{%
    \renewcommand{\EpydocGroup}[1]{\item[##1] \
    }
    \begin{itemize}
        \renewcommand{\makelabel}[1]{\textbf{##1:}}
    \setlength{\parskip}{0ex}}
    {\end{itemize}}

% ======================================================================
% Function Lists

% The EpydocFunctionList environment is used to describe functions
% and methods.  It contains one \EpydocFunction command for each
% function or method.  This command takes eight required arguments:
%
%   - The function's signature: an EpydocFunctionSignature environment
%     specifying the signature for the function.
%
%   - The function's description (from the docstring)
% 
%   - The function's parameters: An EpydocFunctionParameters list 
%     environment providing descriptions of the function's parameters.
%     (from the epydoc @param, @arg, @kwarg, @vararg, @type fields)
%
%   - The function's return description (from the epydoc @rerturns field)
%
%   - The function's return type (from the epydoc @rtype field)
%
%   - The function's exceptions: An EpydocFunctionRaises list
%     environment describing exceptions that the function may raise
%     (from the epydoc @raises field)
%
%   - The function's override: An EpydocFunctionOverrides command
%     describing the method that this function overrides (if any)
%
%   - The function's metadata: Zero or more EpydocMetadata*
%     commands/environments, taken from metadata fields (eg @author)
%
% All arguments except for the first (the signature) may be empty.
%
\define@cmdkeys[Epydoc]{function}{signature,description,parameters,
                                  returndescr,returntype,raises,
                                  overrides,metadata}
\newenvironment{EpydocFunctionList}{%
    \newcommand{\EpydocFunction}[1]{{%
        \setkeys[Epydoc]{function}{##1}%
        {\Large\raggedright\cmdEpydoc@function@signature\par}
        \begin{quote}%
            \setlength{\parskip}{\EpydocParskip}%
            \@ifundefined{cmdEpydoc@function@description}{}{
                \par\cmdEpydoc@function@description}
            \@ifundefined{cmdEpydoc@function@parameters}{}{
                \par\cmdEpydoc@function@parameters}
            \@ifundefined{cmdEpydoc@function@returndescr}{}{
                \par \textbf{Returns:} \cmdEpydoc@function@returndescr}
            \@ifundefined{cmdEpydoc@function@returntype}{}{
                \par \textbf{Return Type:} \cmdEpydoc@function@returntype}
            \@ifundefined{cmdEpydoc@function@raises}{}{
                \par\cmdEpydoc@function@raises}
            \@ifundefined{cmdEpydoc@function@overrides}{}{
                \par\cmdEpydoc@function@overrides}
            \@ifundefined{cmdEpydoc@function@metadata}{}{
                \ifx\cmdEpydoc@function@metadata\empty\else
                    \par\cmdEpydoc@function@metadata\fi}
        \end{quote}\par}}}
  {}

% The EpydocFunctionSignature environment is used to display a
% function's signature.  It expects one argument, the function's
% name.  The body of the environment contains the parameter list.
% The following commands are used in the parameter list, to mark
% individual parameters:
%
%   - \Param: Takes one required argument (the parameter name) and
%     one optional argument (the defaultt value).
%   - \VarArg: Takes one argument (the varargs parameter name)
%   - \KWArg: Takes one argument (the keyword parameter name)
%   - \GenericArg: Takes no arguments (this is used for '...', e.g.
%     when the signature is unknown).
%   - \TupleArg: Used inside of the \Param command, to mark
%     argument tuples.  Individual elements of the argument tuple
%     are separated by the \and command.
% 
% Parameters are separated by the \and command.
\newenvironment{EpydocFunctionSignature}[1]{%
    \newcommand{\and}{, }%
    \newcommand{\VarArg}[1]{*\textit{##1}}%
    \newcommand{\GenericArg}{\textit{\ldots}}%
    \newcommand{\KWArg}[1]{**\textit{##1}}%
    \newcommand{\TupleArg}[1]{(##1)}%
    \newcommand{\Param}[2][]{%
        \textit{##2}%
        \ifthenelse{\equal{##1}{}}{}{=\texttt{##1}}}%
    \@hangfrom{\textbf{#1}(}%
    }{)}

% The EpydocFunctionParameters environment is used to display 
% descriptions for the parameters that a function can take.
% (From epydoc fields: @param, @arg, @kwarg, @vararg, @type)
\newenvironment{EpydocFunctionParameters}[1]{%
    \textbf{Parameters}
    \vspace{-\EpydocParskip}
    \begin{quote}
    \begin{list}{}{%
      \renewcommand{\makelabel}[1]{\texttt{##1:}\hfil}%
      \settowidth{\labelwidth}{\texttt{#1:}}%
      \setlength{\leftmargin}{\labelsep}%
      \addtolength{\leftmargin}{\labelwidth}}}%
    {\end{list}
    \end{quote}
    }

% This environment is used to display descriptions of exceptions
% that can be raised by a function.  (From epydoc field: @raise)
\newenvironment{EpydocFunctionRaises}{%
    \renewcommand*{\descriptionlabel}[1]{\hspace\labelsep
       \normalfont\itshape ##1}
    \textbf{Raises}
    \vspace{-\EpydocParskip}%
    \begin{quote}%
        \begin{description}%
    }
    {\end{description}
    \end{quote}
    }

% This environment is used when a method overrides a base class
% method, to display the name of the overridden method.
\newcommand{\EpydocFunctionOverrides}[2][0]{%
    \textbf{Overrides:} #2 %
    \ifthenelse{#1=1}{\textit{(inherited documentation)}{}}\par}

% ======================================================================
% Variable Lists
%
% There are three separate variable list environments:
%   - EpydocVariableList............ for a module's variables
%   - EpydocInstanceVariableList.... for a class's instance variables
%   - EpydocClassVariableList....... for a class's class variables

% The EpydocVariableList environment is used to describe module
% variables.  It contains one \EpydocVariable command for each
% variable.  This command takes four required arguments:
% 
%   - The variable's name
%   - The variable's description (from the docstring)
%   - The variable's type (from the epydoc @type field)
%   - The variable's value
%
% If any of these arguments is not available, then the empty
% string will be used.
\define@cmdkeys[Epydoc]{variable}{name,description,type,value,metadata}
\newenvironment{EpydocVariableList}{%
    \newcommand{\EpydocVariable}[1]{{%
        \setkeys[Epydoc]{variable}{##1}%
        {\Large\raggedright\cmdEpydoc@variable@name\par}
        \begin{quote}
            \setlength{\parskip}{\EpydocParskip}%
            \@ifundefined{cmdEpydoc@variable@description}{}{%
                \par\cmdEpydoc@variable@description}%
            \@ifundefined{cmdEpydoc@variable@type}{}{%
                \par\textbf{Type:} \cmdEpydoc@variable@type}%
            \@ifundefined{cmdEpydoc@variable@value}{}{%
                \par\textbf{Value:} \cmdEpydoc@variable@value}%
            \@ifundefined{cmdEpydoc@variable@metadata}{}{%
                \ifx\cmdEpydoc@variable@metadata\empty\else
                    \par\cmdEpydoc@variable@metadata\fi}%
        \end{quote}\par}}}
  {}

% The EpydocClassVariableList environment is used the same way as
% the EpydocVariableList environment (shown above).
\newenvironment{EpydocClassVariableList}{%
    \begin{EpydocVariableList}}
    {\end{EpydocVariableList}}

% The EpydocClassVariableList environment is used the same way as
% the EpydocVariableList environment (shown above).
\newenvironment{EpydocInstanceVariableList}{%
    \begin{EpydocVariableList}}
    {\end{EpydocVariableList}}

% ======================================================================
% Property Lists

% The EpydocPropertyList environment is used to describe class
% properties.  It contains one \EpydocProperty command for each
% property.  This command takes six required arguments:
% 
%   - The property's name
%   - The property's description (from the docstring)
%   - The property's type (from the epydoc @type field)
%   - The property's fget function
%   - The property's fset function
%   - The property's fdel function
%
% If any of these arguments is not available, then the empty
% string will be used.
\define@cmdkeys[Epydoc]{property}{name,description,type,fget,
                                  fset,fdel,metadata}
\newenvironment{EpydocPropertyList}{%
    \newcommand{\EpydocProperty}[1]{{%
        \setkeys[Epydoc]{property}{##1}%
        {\Large\raggedright\cmdEpydoc@property@name\par}
        \begin{quote}
            \setlength{\parskip}{\EpydocParskip}%
            \@ifundefined{cmdEpydoc@property@description}{}{
                \par\cmdEpydoc@property@description}
            \@ifundefined{cmdEpydoc@property@type}{}{
                \par\textbf{Type:} \cmdEpydoc@property@type}
            \@ifundefined{cmdEpydoc@property@fget}{}{
                \par\textbf{Get:} \cmdEpydoc@property@fget}
            \@ifundefined{cmdEpydoc@property@fset}{}{
                \par\textbf{Set:} \cmdEpydoc@property@fset}
            \@ifundefined{cmdEpydoc@property@fdel}{}{
                \par\textbf{Delete:} \cmdEpydoc@property@fdel}
            \@ifundefined{cmdEpydoc@property@metadata}{}{
                \ifx\cmdEpydoc@property@metadata\empty\else
                    \par\cmdEpydoc@property@metadata\fi}
        \end{quote}\par}}}
  {}

% ======================================================================
% Metadata

% This command is used to display a metadata field with a single value
\newcommand{\EpydocMetadataSingleValue}[2]{%
    \par
    \begin{list}{}{\itemindent-\leftmargin}
    \item \textbf{#1:} #2
    \end{list}\par\ignorespaces}

% This environment is used to display a metadata field with multiple
% values when the field declares that short=True; i.e., that multiple
% values should be combined into a single comma-delimited list.
\newenvironment{EpydocMetadataShortList}[1]{%
    \newcommand{\and}{, }%
    \par
    \begin{list}{}{\itemindent-\leftmargin}
    \item \textbf{#1:} \ignorespaces}
    {\end{list}\ignorespacesafterend\par}

% This list environment is used to display a metadata field with
% multiple values when the field declares that short=False; i.e., that
% multiple values should be listed separately in a bulleted list.
\newenvironment{EpydocMetadataLongList}[1]{%
    \par
    \textbf{#1:}
    \setlength{\parskip}{0ex}
        \begin{itemize}
            \setlength{\parskip}{\EpydocMetadataLongListParskip}
            \ignorespaces}
    {\end{itemize}\ignorespacesafterend\par}

% ======================================================================
% reStructuredText Admonitions

% This environment is used to display reStructuredText admonitions,
% such as ``..warning::'' and ``..note::''.
\newenvironment{reSTadmonition}[1][]{%
    \begin{center}\begin{sffamily}%
        \begin{lrbox}{\@tempboxa}%
            \begin{minipage}{\admonitionwidth}%
                \textbf{\large #1}%
                \vspace{2mm}}%
    {\end{minipage}%
    \end{lrbox}%
    \fbox{\usebox{\@tempboxa}}%
    \end{sffamily}%
    \end{center}}

% ======================================================================
% Name Formatting    
%
% This section defines the EpydocDottedName command, which is used to
% display the names of Python objects.

% The \EpydocDottedName command adds a possible break-point after every
% period in the given string.  It no longer escapes characters such as
% underscore, since this interfered with the hyperref package; instead,
% epydoc is responsible for escaping them.  E.g., correct usage for a
% name with an underscore is \EpydocDottedName{some\_name}.
\newcommand\EpydocDottedName[1]{
  \Epydoc@DottedName #1.@}

% This helper function performs the work of \EpydocDottedName. It
% scans forward, looking for a period, and putting all text up to the
% period into #1.  The single character after the period is put in
% #2.  This function then checks if #2 is '@', indicating that we're
% done, or some other character, indicating that we should continue.
% Note that \ifx tests character codes; and that when '@' appears
% in user code, it gets the character code 'other', but when it
% appears here, it gets the character code 'letter'.
\def\Epydoc@DottedName#1.#2{%
  \ifx#2@\relax #1\else 
    #1\discretionary{.}{}{.}%
    \expandafter\expandafter\expandafter\Epydoc@DottedName
    \expandafter #2\fi%
  }

"""+NIST_DISCLAIMER
######################################################################
######################################################################

######################################################################
######################################################################
BOXES = r"""
% epydoc-boxes.sty
%
% Authors: Jonathan Guyer <guyer@nist.gov>
%          Edward Loper <edloper@seas.upenn.edu>
% URL: <http://epydoc.sf.net>
%
% This LaTeX stylesheet (nearly) replicates the LaTeX output style
% generated by epydoc 3.0.  Function lists are displayed using
% a boxedminipage for each function.  Variable and Property lists
% are displayed using a longtable, with a row for each object.
%
% $Id:$
\NeedsTeXFormat{LaTeX2e}
\ProvidesClass{epydoc-boxes}[2008/02/26 v3.0.1 Epydoc Python Documentation]
% Pass options to the epydoc base package.
\RequirePackage{xkeyval}
\DeclareOptionX*{\PassOptionsToPackage{\CurrentOption}{epydoc-base}}
\ProcessOptionsX\relax
% Load the base epydoc package
\RequirePackage{epydoc-base}

% Use longtable for variable & property lists.
\RequirePackage{longtable}

% Double the standard size boxedminipage outlines.
\setlength{\fboxrule}{2\fboxrule}

% Set the width of the variable name cells in the variable & property
% tables.
\newlength{\EpydocVariableWidth}
\setlength{\EpydocVariableWidth}{.3\textwidth}

% ======================================================================
% Function Lists

% Put the function inside a boxedminipage.  Use a horizontal rule to
% separate the signature from the description elements.  Implementation
% note: the \@EpydocSeparator command adds a horizontal rule the first
% time it is called, and does nothing when called after that.
\renewenvironment{EpydocFunctionList}{%
    \def\@EpydocSeparator{%
       \vspace{-2\EpydocParskip}
       \rule{\dimexpr \textwidth-2\fboxsep}{0.5\fboxrule}
       \aftergroup\def\aftergroup\@EpydocSeparator%
             \aftergroup{\aftergroup}}%
    \newcommand{\EpydocFunction}[1]{{%
        \setkeys[Epydoc]{function}{##1}%
        \begin{boxedminipage}{\dimexpr \textwidth-2\fboxsep}
            {\Large\raggedright\cmdEpydoc@function@signature\par}
            \setlength{\parskip}{\EpydocParskip}
            \@ifundefined{cmdEpydoc@function@description}{}{%
                {\@EpydocSeparator}%
                \par\cmdEpydoc@function@description}%
            \@ifundefined{cmdEpydoc@function@parameters}{}{%
                {\@EpydocSeparator}%
                \par\cmdEpydoc@function@parameters}%
            \@ifundefined{cmdEpydoc@function@returntype}{
                \@ifundefined{cmdEpydoc@function@returndescr}{}{
                    {\@EpydocSeparator}%
                    \par\textbf{Return Value}%
                    \par\vspace{-\EpydocParskip}%
                    \begin{quote}\cmdEpydoc@function@returndescr\end{quote}}%
            }{
                {\@EpydocSeparator}%
                \par\textbf{Return Value}%
                \par\vspace{-\EpydocParskip}%
                \begin{quote}%
                  \@ifundefined{cmdEpydoc@function@returndescr}{
                      \textit{\cmdEpydoc@function@returntype}%
                  }{%
                      \cmdEpydoc@function@returndescr%
                        \textit{(type=\cmdEpydoc@function@returntype)}}%
                \end{quote}%
            }
            \@ifundefined{cmdEpydoc@function@raises}{}{%
                {\@EpydocSeparator}%
                \par\cmdEpydoc@function@raises}%
            \@ifundefined{cmdEpydoc@function@overrides}{}{%
                {\@EpydocSeparator}%
                \par\cmdEpydoc@function@overrides}%
            \@ifundefined{cmdEpydoc@function@metadata}{}{%
                \ifx\cmdEpydoc@property@metadata\empty\else
                    {\@EpydocSeparator}%
                    \par\cmdEpydoc@function@metadata%
                \fi}%
        \end{boxedminipage}\par}}}
  {}

% ======================================================================
% Multi-Page List (used to define EpydocVariableList etc)

% [xx] \textwidth is not the right size for the multicolumn..

% Define a base environment that we will use to put variable &
% property lists in a longtable.  This environment sets up the
% longtable environment, and redefines the \EpydocGroup and
% \EpydocInheritanceList commands to add a row to the table.
\newenvironment{@EpydocGeneralList}{%
    \renewcommand{\EpydocGroup}[1]{%
        \multicolumn{2}{@{\vrule width \fboxrule \hspace \tabcolsep}l
                        @{\hspace \tabcolsep \vrule width \fboxrule}}
            {\textbf{\textit{##1}}} \\
        \hline}%
    \renewcommand{\EpydocInheritanceList}[2]{%
        \multicolumn{2}{@{\vrule width \fboxrule \hspace \tabcolsep}
                        p{\dimexpr \textwidth -4\tabcolsep-7pt}
                        @{\hspace \tabcolsep \vrule width \fboxrule}}
            {\raggedright\textbf{Inherited from {##1}:\\##2}} \\
        \hline}%
    \setlength{\doublerulesep}{0pt}
    \begin{longtable}[l]{@{\vrule width \fboxrule \hspace \tabcolsep}
                         p{\EpydocVariableWidth}|
                         p{\dimexpr \textwidth%
                                -4\tabcolsep-7pt
                                -\EpydocVariableWidth}
                         @{\hspace \tabcolsep \vrule width \fboxrule}}
    % Set up the headers & footer (this makes the table span
    % multiple pages in a happy way).
    \hline \hline \rule{0pt}{\baselineskip} 
    \centering \Large \textbf{Name} &
    \centering \Large \textbf{Description} 
    \tabularnewline
    \hline \hline 
    \endhead%
    \hline\hline\multicolumn{2}{r}{%
        \small\textit{continued on next page}}\\\endfoot%
    \hline\hline
    \endlastfoot}
    {\end{longtable}}

% ======================================================================
% Variable Lists

\renewenvironment{EpydocVariableList}{%
    \newcommand{\EpydocVariable}[1]{{%
        \setkeys[Epydoc]{variable}{##1}%
        \raggedright\cmdEpydoc@variable@name &%
        \setkeys[Epydoc]{variable}{##1}%
        \setlength{\parskip}{\EpydocParskip}\raggedright%
        \@ifundefined{cmdEpydoc@variable@description}{}{%
            \cmdEpydoc@variable@description\relax}%
        \@ifundefined{cmdEpydoc@variable@value}{}{%
            \@ifundefined{cmdEpydoc@variable@description}{}{\par}%
            \textbf{Value:} \texttt{\cmdEpydoc@variable@value}}%
        \@ifundefined{cmdEpydoc@variable@type}{}{%
            \@ifundefined{cmdEpydoc@variable@description}{%
                \@ifundefined{cmdEpydoc@variable@value}{}{ }}{ }%
            \textit{(type=\texttt{\cmdEpydoc@variable@type})}}%
        \tabularnewline
        \hline}}%
    \begin{@EpydocGeneralList}%
    }
    {\end{@EpydocGeneralList}}

% By default, EpydocClassVariableList & EpydocInstanceVariableList are 
% just aliases for EpydocVaribleList.

% ======================================================================
% Property Lists

% Implementation node: \@EpydocSeparator evaluates to nothing on
% the first use, and to a paragraph break on subsequent uses.
\renewenvironment{EpydocPropertyList}{%
    \def\@EpydocSeparator{%
       \aftergroup\def\aftergroup\@EpydocSeparator\aftergroup{%
       \aftergroup\par%
       \aftergroup}}%
    \newcommand{\EpydocProperty}[1]{{%
        \setkeys[Epydoc]{property}{##1}%
        \raggedright\cmdEpydoc@property@name & %
        \setkeys[Epydoc]{property}{##1}%
        \setlength{\parskip}{\EpydocParskip}\raggedright%
        \@ifundefined{cmdEpydoc@property@description}{}{%
            {\@EpydocSeparator}%
            \cmdEpydoc@property@description\relax}%
        \@ifundefined{cmdEpydoc@property@type}{}{%
            {\@EpydocSeparator}%
            \textbf{Type:} \cmdEpydoc@property@type\relax}%
        \@ifundefined{cmdEpydoc@property@fget}{}{%
            {\@EpydocSeparator}%
            \textbf{Get:} \cmdEpydoc@property@fget\relax}%
        \@ifundefined{cmdEpydoc@property@fset}{}{%
            {\@EpydocSeparator}%
            \textbf{Set:} \cmdEpydoc@property@fset\relax}%
        \@ifundefined{cmdEpydoc@property@fdel}{}{%
            {\@EpydocSeparator}%
            \textbf{Delete:} \cmdEpydoc@property@fdel\relax}%
        \tabularnewline
        \hline}}
    \begin{@EpydocGeneralList}}
    {\end{@EpydocGeneralList}}
"""+NIST_DISCLAIMER
######################################################################
######################################################################


######################################################################
######################################################################
SHADED = r"""
% epydoc-shaded.sty
%
% Author: Edward Loper <edloper@seas.upenn.edu>
% URL: <http://epydoc.sf.net>
%
% This LaTeX stylesheet for epydoc's output uses shaded boxes to
% display the function, variable, and property lists.  Each
% object's name (or signature) is displayed in a lightly shaded
% box, and is immediately followed by a shaded and indented box 
% containing a description of that object:
%
%         +-------------------------------------------+
%         | object's name                             |
%         +-------------------------------------------+
%             | description of the object             |
%             | ...                                   |
%             +---------------------------------------+
%
% $Id:$
\NeedsTeXFormat{LaTeX2e}
\ProvidesClass{epydoc-shaded}[2008/02/26 v3.0.1 Epydoc Python Documentation]
% Pass options to the epydoc base package.
\RequirePackage{xkeyval}
\DeclareOptionX*{\PassOptionsToPackage{\CurrentOption}{epydoc-base}}
\ProcessOptionsX\relax
% Load the base epydoc package
\RequirePackage{epydoc-base}

% ======================================================================
% Customization hooks

% These colors 
\definecolor{EpydocNameColor}{gray}{0.95}
\definecolor{EpydocDetailsColor}{gray}{0.90}
\definecolor{EpydocValueColor}{gray}{0.85}
\definecolor{EpydocGroupColor}{gray}{0.8}
\definecolor{EpydocInheritanceListColor}{gray}{0.95}

% This length controls how tightly function, variable, and property
% entries are spaced.
\newlength{\EpydocListsep}
\setlength{\EpydocListsep}{\baselineskip}

% This length is used to dedent the section headings.
\newlength{\EpydocSectionHeaderDedent}
\setlength{\EpydocSectionHeaderDedent}{1cm}

% ======================================================================
% Colored minipage

% adapted from <http://www.texnik.de/color/color.phtml> for colored 
% paragraph boxes
\newcommand{\cmcolor}{}
\newenvironment{cminipage}[2][white]{%
    \renewcommand{\cmcolor}{#1}%
    \begin{lrbox}{\@tempboxa}%
      \begin{minipage}{#2}%
      \setlength{\parskip}{\EpydocParskip}%
  }{%
      \end{minipage}%
    \end{lrbox}%
    \colorbox{\cmcolor}{\usebox{\@tempboxa}}}

% ======================================================================
% Redefinitions

\renewenvironment{EpydocFunctionList}{%
  \setlength{\parskip}{\EpydocListsep}%
  \newcommand{\EpydocFunction}[1]{{
    \setkeys[Epydoc]{function}{##1}%
    % Decide whether to include a 'details' block
    \newif\ifEpydoc@details%
    \@ifundefined{cmdEpydoc@function@description}{}{\Epydoc@detailstrue}%
    \@ifundefined{cmdEpydoc@function@parameters}{}{\Epydoc@detailstrue}%
    \@ifundefined{cmdEpydoc@function@returndescr}{}{\Epydoc@detailstrue}%
    \@ifundefined{cmdEpydoc@function@returntype}{}{\Epydoc@detailstrue}%
    \@ifundefined{cmdEpydoc@function@raises}{}{\Epydoc@detailstrue}%
    \@ifundefined{cmdEpydoc@function@overrides}{}{\Epydoc@detailstrue}%
    \@ifundefined{cmdEpydoc@function@metadata}{}{\Epydoc@detailstrue}%
    % Add some vertical space.
    \par
    % Put a box around the whole thing, so the signature line and the
    % body don't get split across pages.
    \begin{minipage}{\textwidth}%
      \setlength{\parskip}{-1pt}\raggedleft%
      % Add the function signature in a gray95 box.
      \begin{cminipage}[EpydocNameColor]%
                       {\dimexpr \textwidth-2\fboxsep}
        \Large\raggedright\cmdEpydoc@function@signature
      \end{cminipage}\par
      % Add the details in a gray90 box.
      \ifEpydoc@details
        \begin{cminipage}[EpydocDetailsColor]%
                         {\dimexpr 0.95\linewidth-2\fboxsep}%
          \@ifundefined{cmdEpydoc@function@description}{}{%
              \par\cmdEpydoc@function@description}%
          \@ifundefined{cmdEpydoc@function@parameters}{}{%
              \par\cmdEpydoc@function@parameters}%
          \@ifundefined{cmdEpydoc@function@returntype}{
              \@ifundefined{cmdEpydoc@function@returndescr}{}{
                  \par\textbf{Return Value}%
                  \par\vspace{-\EpydocParskip}%
                  \begin{quote}\cmdEpydoc@function@returndescr%
                  \end{quote}}%
          }{
              \par\textbf{Return Value}%
              \par\vspace{-\EpydocParskip}%
              \begin{quote}%
                \@ifundefined{cmdEpydoc@function@returndescr}{
                    \textit{\cmdEpydoc@function@returntype}%
                }{%
                    \cmdEpydoc@function@returndescr%
                      \textit{(type=\cmdEpydoc@function@returntype)}}%
              \end{quote}}%
          \@ifundefined{cmdEpydoc@function@raises}{}{%
              \par\cmdEpydoc@function@raises}%
          \@ifundefined{cmdEpydoc@function@overrides}{}{%
              \par\cmdEpydoc@function@overrides}%
          \@ifundefined{cmdEpydoc@function@metadata}{}{%
              \par\cmdEpydoc@function@metadata}%
        \end{cminipage}\par
      \fi%
   \end{minipage}\par}}}
  {}

\newlength{\EpydocValueWidth}
\renewenvironment{EpydocVariableList}{%
  \setlength{\parskip}{\EpydocListsep}%
  \newcommand{\EpydocVariable}[1]{{
    \setkeys[Epydoc]{variable}{##1}%
    % Decide whether to include a 'details' block
    \newif\ifEpydoc@details%
    \@ifundefined{cmdEpydoc@variable@description}{}{\Epydoc@detailstrue}%
    \@ifundefined{cmdEpydoc@variable@value}{}{\Epydoc@detailstrue}%
    \@ifundefined{cmdEpydoc@variable@type}{}{\Epydoc@detailstrue}%
    % Put a box around the whole thing, so the variable's name line 
    % and the body don't get split across pages.
    \begin{minipage}{\linewidth}%
      \setlength{\parskip}{-1pt}\raggedleft%
      % Add the variable name in a gray95 box.
      \begin{cminipage}[EpydocNameColor]%
                       {\dimexpr \textwidth-2\fboxsep}
        \Large \bfseries \cmdEpydoc@variable@name%
      \end{cminipage}\par
      % Add the details in a gray90 box.
      \ifEpydoc@details
        \begin{cminipage}[EpydocDetailsColor]%
                         {\dimexpr 0.95\linewidth-2\fboxsep}%
          \setlength{\parskip}{\EpydocParskip}%
          \@ifundefined{cmdEpydoc@variable@description}{}{%
              \par\cmdEpydoc@variable@description}%
          \@ifundefined{cmdEpydoc@variable@type}{}{%
              \par\textbf{Type:} \texttt{\cmdEpydoc@variable@type}}%
          \@ifundefined{cmdEpydoc@variable@value}{}{%
            \settowidth{\EpydocValueWidth}{Value:w}%
            \par Value:
            \begin{cminipage}[EpydocValueColor]%
                             {\dimexpr \textwidth-2\fboxsep-\EpydocValueWidth}
              \texttt{\cmdEpydoc@variable@value}%
            \end{cminipage}}\par
        \end{cminipage}\par
      \fi%
    \end{minipage}\par}}}
  {}

\renewenvironment{EpydocPropertyList}{%
  \setlength{\parskip}{\EpydocListsep}%
  \newcommand{\EpydocProperty}[1]{{%
    \setkeys[Epydoc]{property}{##1}%
    % Decide whether to include a 'details' block
    \newif\ifEpydoc@details%
    \@ifundefined{cmdEpydoc@property@description}{}{\Epydoc@detailstrue}%
    \@ifundefined{cmdEpydoc@property@type}{}{\Epydoc@detailstrue}%
    \@ifundefined{cmdEpydoc@property@fget}{}{\Epydoc@detailstrue}%
    \@ifundefined{cmdEpydoc@property@fset}{}{\Epydoc@detailstrue}%
    \@ifundefined{cmdEpydoc@property@fdel}{}{\Epydoc@detailstrue}%
    % Put a box around the whole thing, so the property's name line 
    % and the body don't get split across pages.
    \begin{minipage}{\linewidth}%
      \setlength{\parskip}{-1pt}\raggedleft%
      % Add the variable name in a gray95 box.
      \begin{cminipage}[EpydocNameColor]
                       {\dimexpr \textwidth-2\fboxsep}
        \Large \bfseries \cmdEpydoc@property@name%
      \end{cminipage}\par
      % Add the details in a gray90 box.
      \ifEpydoc@details
        \begin{cminipage}[EpydocDetailsColor]
                         {\dimexpr 0.95\linewidth-2\fboxsep}
          \setlength{\parskip}{\EpydocParskip}
          \@ifundefined{cmdEpydoc@property@description}{}{%
              \par\cmdEpydoc@property@description}%
          \@ifundefined{cmdEpydoc@property@type}{}{%
              \par\textbf{Type:} \cmdEpydoc@property@type}%
          \@ifundefined{cmdEpydoc@property@fget}{}{%
              \par\textbf{Get:} \cmdEpydoc@property@fget}%
          \@ifundefined{cmdEpydoc@property@fset}{}{%
              \par\textbf{Set:} \cmdEpydoc@property@fset}%
          \@ifundefined{cmdEpydoc@property@fdel}{}{%
              \par\textbf{Delete:} \cmdEpydoc@property@fdel}%
        \end{cminipage}\par
      \fi%
    \end{minipage}\par}}}
  {}

\renewcommand{\EpydocGroup}[1]{\par
  \begin{cminipage}[EpydocGroupColor]
                   {\dimexpr \linewidth-2\fboxsep}
    {\Large\bf\center #1\\}
  \end{cminipage}\par}
\renewcommand{\EpydocInheritanceList}[2]{\par
  \begin{cminipage}[EpydocInheritanceListColor]
                   {\dimexpr \textwidth-2\fboxsep}
  \raggedright%
  Inherited from {#1}: #2%
  \end{cminipage}\par}

% This is just like the default definitions, except that we use
% \raggedright, and dedent by \EpydocSectionHeaderDedent
\renewcommand\section{\@startsection {section}{1}%
              {-\EpydocSectionHeaderDedent}%
              {-3.5ex \@plus -1ex \@minus -.2ex}%
              {2.3ex \@plus.2ex}%
              {\raggedright\normalfont\Large\bfseries}}
\renewcommand\subsection{\@startsection{subsection}{2}%
              {-\EpydocSectionHeaderDedent}%
              {-3.25ex\@plus -1ex \@minus -.2ex}%
              {1.5ex \@plus .2ex}%
              {\raggedright\normalfont\large\bfseries}}
\renewcommand\subsubsection{\@startsection{subsubsection}{3}%
              {-\EpydocSectionHeaderDedent}%
              {-3.25ex\@plus -1ex \@minus -.2ex}%
              {1.5ex \@plus .2ex}%
              {\raggedright\normalfont\normalsize\bfseries}}
"""

######################################################################
######################################################################

BLUE = r"""
% epydoc-blue.sty
%
% A relatively minimal customization of a builtin epydoc style file,
% showing the basic pieces that need to be present.
%
\NeedsTeXFormat{LaTeX2e}
\ProvidesClass{epydoc-blue}[2008/02/26 v3.0.1 Epydoc Python Documentation]

% Load our base package (epydoc-shaded in this case).
\RequirePackage{xkeyval}
\DeclareOptionX*{\PassOptionsToPackage{\CurrentOption}{epydoc-shaded}}
\ProcessOptionsX\relax
\RequirePackage{epydoc-shaded}

% Perform some customizations
\definecolor{EpydocNameColor}{rgb}{.9,.95,1}
\definecolor{EpydocDetailsColor}{rgb}{.8,.9,1}
\definecolor{EpydocValueColor}{rgb}{.7,.85,1}
\definecolor{EpydocGroupColor}{rgb}{.6,.8,1}
\definecolor{EpydocInheritanceListColor}{rgb}{.9,.95,1}
"""

######################################################################
######################################################################

TEMPLATE = r"""
% epydoc-template.sty
%
% This is a starting point for creating new epydoc style files.
% Add on \renewcommand and \renewenvironment commands to change
% how different pieces of the documentation are displayed.
%
\NeedsTeXFormat{LaTeX2e}

% Replace 'XXX' with a new name, and put in the current date:
\ProvidesClass{epydoc-XXX}[2008/02/26 v3.0.1 Epydoc Python Documentation]

% Pass options to the epydoc base package.
\RequirePackage{xkeyval}
\DeclareOptionX*{\PassOptionsToPackage{\CurrentOption}{epydoc-base}}
\ProcessOptionsX\relax

\RequirePackage{epydoc-base}

% Add \renewcommand and \renewenvironment commands here.
"""

############################################################
## Stylesheet table
############################################################

STYLESHEETS = {
    'base': BASE,
    'boxes': BOXES,
    'shaded': SHADED,
    'default': BOXES,
    'template': TEMPLATE,
    'blue': BLUE,
}
