/*
 * (C) Copyright 2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * Contributors:
 * Nuxeo - initial API and implementation
 */

package org.nuxeo.ecm.platform.template.pub.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.platform.rendition.Constants.RENDITION_FACET;
import static org.nuxeo.ecm.platform.rendition.Constants.RENDITION_SCHEMA;
import static org.nuxeo.ecm.platform.rendition.publisher.RenditionPublicationFactory.RENDITION_NAME_PARAMETER_KEY;

import java.io.File;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.publisher.api.PublicationNode;
import org.nuxeo.ecm.platform.publisher.api.PublicationTree;
import org.nuxeo.ecm.platform.publisher.api.PublisherService;
import org.nuxeo.ecm.platform.publisher.impl.core.SimpleCorePublishedDocument;
import org.nuxeo.ecm.platform.rendition.service.RenditionDefinition;
import org.nuxeo.ecm.platform.rendition.service.RenditionService;
import org.nuxeo.ecm.platform.task.test.TaskUTConstants;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;
import org.nuxeo.template.api.InputType;
import org.nuxeo.template.api.TemplateInput;
import org.nuxeo.template.api.TemplateProcessorService;
import org.nuxeo.template.api.adapters.TemplateBasedDocument;
import org.nuxeo.template.api.adapters.TemplateSourceDocument;
import org.nuxeo.template.processors.HtmlBodyExtractor;

@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@RepositoryConfig(init = RenditionPublicationRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy({ "org.nuxeo.ecm.core.mimetype", "org.nuxeo.ecm.core.convert.api", "org.nuxeo.ecm.core.convert",
        "org.nuxeo.ecm.platform.query.api", "org.nuxeo.ecm.core.convert.plugins", "org.nuxeo.ecm.platform.convert",
        "org.nuxeo.ecm.actions", "org.nuxeo.ecm.platform.rendition.api", "org.nuxeo.ecm.platform.rendition.core",
        "org.nuxeo.ecm.platform.rendition.publisher", "org.nuxeo.ecm.automation.core",
        "org.nuxeo.ecm.platform.versioning.api", "org.nuxeo.ecm.platform.versioning", "org.nuxeo.ecm.relations",
        "org.nuxeo.ecm.relations.jena", "org.nuxeo.ecm.platform.publisher.core.contrib",
        "org.nuxeo.ecm.platform.publisher.core", "org.nuxeo.ecm.platform.publisher.task",
        TaskUTConstants.CORE_BUNDLE_NAME, TaskUTConstants.TESTING_BUNDLE_NAME,
        "org.nuxeo.ecm.platform.rendition.publisher", "org.nuxeo.template.manager" })
@LocalDeploy("org.nuxeo.template.manager:relations-default-jena-contrib.xml")
public class TestRenditionPublication {

    protected static final String TEMPLATE_NAME = "mytestTemplate";

    @Inject
    protected CoreSession session;

    @Inject
    protected PublisherService publisherService;

    @Inject
    protected RenditionService renditionService;

    @Inject
    TemplateProcessorService tps;

    protected DocumentModel createTemplateBasedDoc() throws Exception {

        DocumentModel root = session.getRootDocument();

        // create the template
        DocumentModel templateDoc = session.createDocumentModel(root.getPathAsString(), "templatedDoc",
                "TemplateSource");
        templateDoc.setProperty("dublincore", "title", "MyTemplate");
        // File file =
        // FileUtils.getResourceFileFromContext("data/DocumentsAttributes.odt");
        File file = FileUtils.getResourceFileFromContext("data/htmlRender.ftl");
        Blob fileBlob = Blobs.createBlob(file);
        fileBlob.setFilename("htmlRendered.ftl");
        templateDoc.setProperty("file", "content", fileBlob);
        templateDoc.setPropertyValue("tmpl:templateName", TEMPLATE_NAME);

        templateDoc = session.createDocument(templateDoc);

        // configure rendition and output format
        TemplateSourceDocument source = templateDoc.getAdapter(TemplateSourceDocument.class);
        source.setTargetRenditioName("webView", false);

        // check that parameter has been detected
        assertEquals(1, source.getParams().size());

        // value parameter
        TemplateInput param = new TemplateInput("htmlContent", "htmlPreview");
        param.setType(InputType.Content);
        param.setSource("htmlPreview");
        source.addInput(param);

        // update the doc and adapter
        templateDoc = session.saveDocument(source.getAdaptedDoc());
        source = templateDoc.getAdapter(TemplateSourceDocument.class);

        assertEquals(1, source.getParams().size());
        TemplateInput inputParam = source.getParams().get(0);
        assertEquals("htmlContent", inputParam.getName());
        assertEquals("htmlPreview", inputParam.getSource());

        // create the Note
        DocumentModel testDoc = session.createDocumentModel(root.getPathAsString(), "testDoc", "Note");
        testDoc.setProperty("dublincore", "title", "MyTestNoteDoc");
        testDoc.setProperty("dublincore", "description", "Simple note sample");

        testDoc.setProperty("note", "note", "<html><body><p> Simple <b> Note </b> with <i>text</i></body></html>");

        testDoc = session.createDocument(testDoc);

        // associate File to template
        testDoc = tps.makeTemplateBasedDocument(testDoc, templateDoc, true);

        TemplateBasedDocument templateBased = testDoc.getAdapter(TemplateBasedDocument.class);
        assertNotNull(templateBased);

        return testDoc;
    }

    @Test
    public void verifyRenditionBinding() throws Exception {

        DocumentModel templateBasedDoc = createTemplateBasedDoc();
        TemplateBasedDocument templateBased = templateBasedDoc.getAdapter(TemplateBasedDocument.class);
        assertNotNull(templateBased);

        templateBased.getSourceTemplate(TEMPLATE_NAME).setTargetRenditioName(null, true);

        List<RenditionDefinition> defs = renditionService.getAvailableRenditionDefinitions(templateBasedDoc);
        // one blob => pdf rendition, + export renditions
        assertEquals(4, defs.size());

        templateBased.getSourceTemplate(TEMPLATE_NAME).setTargetRenditioName("webView", true);
        defs = renditionService.getAvailableRenditionDefinitions(templateBasedDoc);
        // blob, + delivery rendition binding + export renditions => 5 rendition
        assertEquals(5, defs.size());

    }

    @Test
    public void shouldPublishATemplateRendition() throws Exception {

        // setup tree
        String defaultTreeName = publisherService.getAvailablePublicationTree().get(0);
        PublicationTree tree = publisherService.getPublicationTree(defaultTreeName, session, null);

        List<PublicationNode> nodes = tree.getChildrenNodes();
        assertEquals(1, nodes.size());
        assertEquals("Section1", nodes.get(0).getTitle());

        PublicationNode targetNode = nodes.get(0);
        assertTrue(tree.canPublishTo(targetNode));

        // create a template doc
        DocumentModel templateBasedDoc = createTemplateBasedDoc();

        // publish
        SimpleCorePublishedDocument publishedDocument = (SimpleCorePublishedDocument) tree.publish(templateBasedDoc,
                targetNode, Collections.singletonMap(RENDITION_NAME_PARAMETER_KEY, "webView"));

        // check rendition is done
        DocumentModel proxy = publishedDocument.getProxy();
        assertTrue(proxy.hasFacet(RENDITION_FACET));
        assertTrue(proxy.hasSchema(RENDITION_SCHEMA));

        BlobHolder bh = proxy.getAdapter(BlobHolder.class);
        Blob renditionBlob = bh.getBlob();
        assertNotNull(renditionBlob);

        String htmlPage = renditionBlob.getString();
        assertNotNull(htmlPage);
        System.out.print(htmlPage);
        assertTrue(htmlPage.contains((String) templateBasedDoc.getPropertyValue("dc:description")));
        assertTrue(htmlPage.contains(templateBasedDoc.getTitle()));
        String noteHtmlContent = HtmlBodyExtractor.extractHtmlBody((String) templateBasedDoc.getPropertyValue("note:note"));
        assertNotNull(noteHtmlContent);
        System.out.print(noteHtmlContent);
        assertTrue(htmlPage.contains(noteHtmlContent));

        // verify html body extraction
        int bodyIdx = htmlPage.indexOf("<body>");
        assertTrue(bodyIdx > 0); // at least one body tag
        assertTrue(htmlPage.indexOf("<body>", bodyIdx + 1) < 0); // but not 2

        // refetch !?
        proxy = session.getDocument(proxy.getRef());
        assertEquals("0.1", proxy.getVersionLabel());

        // update template
        templateBasedDoc.setPropertyValue("dc:description", "updated!");
        templateBasedDoc = session.saveDocument(templateBasedDoc);
        session.save();
        assertEquals("0.1+", templateBasedDoc.getVersionLabel());

        // republish
        publishedDocument = (SimpleCorePublishedDocument) tree.publish(templateBasedDoc, targetNode,
                Collections.singletonMap(RENDITION_NAME_PARAMETER_KEY, "webView"));

        proxy = publishedDocument.getProxy();
        assertTrue(proxy.hasFacet(RENDITION_FACET));
        assertTrue(proxy.hasSchema(RENDITION_SCHEMA));

        bh = proxy.getAdapter(BlobHolder.class);
        renditionBlob = bh.getBlob();
        assertNotNull(renditionBlob);

        // refetch !?
        proxy = session.getDocument(proxy.getRef());
        assertEquals("0.2", proxy.getVersionLabel());

    }
}
