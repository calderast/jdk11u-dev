/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.javadoc.internal.doclets.formats.html;

import java.util.*;

import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;
import jdk.javadoc.internal.doclets.toolkit.AbstractDoclet;
import jdk.javadoc.internal.doclets.toolkit.DocletException;
import jdk.javadoc.internal.doclets.toolkit.Messages;
import jdk.javadoc.internal.doclets.toolkit.builders.AbstractBuilder;
import jdk.javadoc.internal.doclets.toolkit.util.ClassTree;
import jdk.javadoc.internal.doclets.toolkit.util.DocFile;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.IndexBuilder;

/**
 * The class with "start" method, calls individual Writers.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Atul M Dambalkar
 * @author Robert Field
 * @author Jamie Ho
 *
 */
public class HtmlDoclet extends AbstractDoclet {

    public HtmlDoclet(Doclet parent) {
        configuration = new HtmlConfiguration(parent);
    }

    @Override // defined by Doclet
    public String getName() {
        return "Html";
    }

    /**
     * The global configuration information for this run.
     */
    private final HtmlConfiguration configuration;

    private Messages messages;


    private static final DocPath DOCLET_RESOURCES = DocPath
            .create("/jdk/javadoc/internal/doclets/formats/html/resources");

    @Override // defined by Doclet
    public void init(Locale locale, Reporter reporter) {
        configuration.reporter = reporter;
        configuration.locale = locale;
        messages = configuration.getMessages();
    }

    /**
     * Create the configuration instance.
     * Override this method to use a different
     * configuration.
     *
     * @return the configuration
     */
    @Override // defined by AbstractDoclet
    public HtmlConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Start the generation of files. Call generate methods in the individual
     * writers, which will in turn generate the documentation files. Call the
     * TreeWriter generation first to ensure the Class Hierarchy is built
     * first and then can be used in the later generation.
     *
     * For new format.
     *
     * @throws DocletException if there is a problem while writing the other files
     */
    @Override // defined by AbstractDoclet
    protected void generateOtherFiles(DocletEnvironment docEnv, ClassTree classtree)
            throws DocletException {
        super.generateOtherFiles(docEnv, classtree);
        if (configuration.linksource) {
            SourceToHTMLConverter.convertRoot(configuration,
                docEnv, DocPaths.SOURCE_OUTPUT);
        }
        // Modules with no documented classes may be specified on the
        // command line to specify a service provider, allow these.
        if (configuration.getSpecifiedModuleElements().isEmpty() &&
                configuration.topFile.isEmpty()) {
            messages.error("doclet.No_Non_Deprecated_Classes_To_Document");
            return;
        }
        boolean nodeprecated = configuration.nodeprecated;
        performCopy(configuration.helpfile);
        performCopy(configuration.stylesheetfile);
        for (String stylesheet : configuration.additionalStylesheets) {
            performCopy(stylesheet);
        }
        // do early to reduce memory footprint
        if (configuration.classuse) {
            ClassUseWriter.generate(configuration, classtree);
        }
        IndexBuilder indexbuilder = new IndexBuilder(configuration, nodeprecated);

        if (configuration.createtree) {
            TreeWriter.generate(configuration, classtree);
        }
        if (configuration.createindex) {
            configuration.buildSearchTagIndex();
            if (configuration.splitindex) {
                SplitIndexWriter.generate(configuration, indexbuilder);
            } else {
                SingleIndexWriter.generate(configuration, indexbuilder);
            }
            AllClassesIndexWriter.generate(configuration,
                    new IndexBuilder(configuration, nodeprecated, true));
            if (!configuration.packages.isEmpty()) {
                AllPackagesIndexWriter.generate(configuration);
            }
        }

        if (!(configuration.nodeprecatedlist || nodeprecated)) {
            DeprecatedListWriter.generate(configuration);
        }

        AllClassesFrameWriter.generate(configuration,
            new IndexBuilder(configuration, nodeprecated, true));

        if (configuration.frames) {
            FrameOutputWriter.generate(configuration);
        }

        if (configuration.createoverview) {
            if (configuration.showModules) {
                ModuleIndexWriter.generate(configuration);
            } else {
                PackageIndexWriter.generate(configuration);
            }
        }

        if (!configuration.frames) {
            if (configuration.createoverview) {
                IndexRedirectWriter.generate(configuration, DocPaths.OVERVIEW_SUMMARY, DocPaths.INDEX);
            } else {
                IndexRedirectWriter.generate(configuration);
            }
        }

        if (configuration.helpfile.isEmpty() && !configuration.nohelp) {
            HelpWriter.generate(configuration);
        }
        // If a stylesheet file is not specified, copy the default stylesheet
        // and replace newline with platform-specific newline.
        DocFile f;
        if (configuration.stylesheetfile.length() == 0) {
            f = DocFile.createFileForOutput(configuration, DocPaths.STYLESHEET);
            f.copyResource(DocPaths.RESOURCES.resolve(DocPaths.STYLESHEET), true, true);
        }
        f = DocFile.createFileForOutput(configuration, DocPaths.JAVASCRIPT);
        f.copyResource(DocPaths.RESOURCES.resolve(DocPaths.JAVASCRIPT), true, true);
        if (configuration.createindex) {
            f = DocFile.createFileForOutput(configuration, DocPaths.SEARCH_JS);
            f.copyResource(DOCLET_RESOURCES.resolve(DocPaths.SEARCH_JS), true, true);

            f = DocFile.createFileForOutput(configuration, DocPaths.RESOURCES.resolve(DocPaths.GLASS_IMG));
            f.copyResource(DOCLET_RESOURCES.resolve(DocPaths.GLASS_IMG), true, false);

            f = DocFile.createFileForOutput(configuration, DocPaths.RESOURCES.resolve(DocPaths.X_IMG));
            f.copyResource(DOCLET_RESOURCES.resolve(DocPaths.X_IMG), true, false);
            copyJqueryFiles();

            f = DocFile.createFileForOutput(configuration, DocPaths.JQUERY_OVERRIDES_CSS);
            f.copyResource(DOCLET_RESOURCES.resolve(DocPaths.JQUERY_OVERRIDES_CSS), true, true);
        }
    }

    private void copyJqueryFiles() throws DocletException {
        List<String> files = Arrays.asList(
                "jquery-3.6.0.min.js",
                "jquery-ui.min.js",
                "jquery-ui.min.css",
                "jquery-ui.structure.min.css",
                "external/jquery/jquery.js",
                "jszip/dist/jszip.js",
                "jszip/dist/jszip.min.js",
                "jszip-utils/dist/jszip-utils.js",
                "jszip-utils/dist/jszip-utils.min.js",
                "jszip-utils/dist/jszip-utils-ie.js",
                "jszip-utils/dist/jszip-utils-ie.min.js",
                "images/ui-bg_glass_65_dadada_1x400.png",
                "images/ui-icons_454545_256x240.png",
                "images/ui-bg_glass_95_fef1ec_1x400.png",
                "images/ui-bg_glass_75_dadada_1x400.png",
                "images/ui-bg_highlight-soft_75_cccccc_1x100.png",
                "images/ui-icons_888888_256x240.png",
                "images/ui-icons_2e83ff_256x240.png",
                "images/ui-icons_cd0a0a_256x240.png",
                "images/ui-bg_glass_55_fbf9ee_1x400.png",
                "images/ui-icons_222222_256x240.png",
                "images/ui-bg_glass_75_e6e6e6_1x400.png");
        DocFile f;
        for (String file : files) {
            DocPath filePath = DocPaths.JQUERY_FILES.resolve(file);
            f = DocFile.createFileForOutput(configuration, filePath);
            f.copyResource(DOCLET_RESOURCES.resolve(filePath), true, false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override // defined by AbstractDoclet
    protected void generateClassFiles(SortedSet<TypeElement> arr, ClassTree classtree)
            throws DocletException {
        List<TypeElement> list = new ArrayList<>(arr);
        for (TypeElement klass : list) {
            if (utils.hasHiddenTag(klass) ||
                    !(configuration.isGeneratedDoc(klass) && utils.isIncluded(klass))) {
                continue;
            }
            if (utils.isAnnotationType(klass)) {
                AbstractBuilder annotationTypeBuilder =
                    configuration.getBuilderFactory()
                        .getAnnotationTypeBuilder(klass);
                annotationTypeBuilder.build();
            } else {
                AbstractBuilder classBuilder =
                    configuration.getBuilderFactory().getClassBuilder(klass, classtree);
                classBuilder.build();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override // defined by AbstractDoclet
    protected void generateModuleFiles() throws DocletException {
        if (configuration.showModules) {
            if (configuration.frames  && configuration.modules.size() > 1) {
                ModuleIndexFrameWriter.generate(configuration);
            }
            List<ModuleElement> mdles = new ArrayList<>(configuration.modulePackages.keySet());
            for (ModuleElement mdle : mdles) {
                if (configuration.frames && configuration.modules.size() > 1) {
                    ModulePackageIndexFrameWriter.generate(configuration, mdle);
                    ModuleFrameWriter.generate(configuration, mdle);
                }
                AbstractBuilder moduleSummaryBuilder =
                        configuration.getBuilderFactory().getModuleSummaryBuilder(mdle);
                moduleSummaryBuilder.build();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override // defined by AbstractDoclet
    protected void generatePackageFiles(ClassTree classtree) throws DocletException {
        Set<PackageElement> packages = configuration.packages;
        if (packages.size() > 1 && configuration.frames) {
            PackageIndexFrameWriter.generate(configuration);
        }
        List<PackageElement> pList = new ArrayList<>(packages);
        for (PackageElement pkg : pList) {
            // if -nodeprecated option is set and the package is marked as
            // deprecated, do not generate the package-summary.html, package-frame.html
            // and package-tree.html pages for that package.
            if (!(configuration.nodeprecated && utils.isDeprecated(pkg))) {
                if (configuration.frames) {
                    PackageFrameWriter.generate(configuration, pkg);
                }
                AbstractBuilder packageSummaryBuilder =
                        configuration.getBuilderFactory().getPackageSummaryBuilder(pkg);
                packageSummaryBuilder.build();
                if (configuration.createtree) {
                    PackageTreeWriter.generate(configuration, pkg, configuration.nodeprecated);
                }
            }
        }
    }

    @Override // defined by Doclet
    public Set<Option> getSupportedOptions() {
        return configuration.getSupportedOptions();
    }

    private void performCopy(String filename) throws DocFileIOException {
        if (filename.isEmpty())
            return;

        DocFile fromfile = DocFile.createFileForInput(configuration, filename);
        DocPath path = DocPath.create(fromfile.getName());
        DocFile toFile = DocFile.createFileForOutput(configuration, path);
        if (toFile.isSameFile(fromfile))
            return;

        messages.notice("doclet.Copying_File_0_To_File_1",
                fromfile.toString(), path.getPath());
        toFile.copyFile(fromfile);
    }
}
