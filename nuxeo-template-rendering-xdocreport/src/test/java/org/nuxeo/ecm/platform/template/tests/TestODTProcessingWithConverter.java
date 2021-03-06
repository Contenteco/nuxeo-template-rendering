package org.nuxeo.ecm.platform.template.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.TransactionalFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.convert.ConvertHelper;
import org.nuxeo.ecm.platform.convert.ooomanager.OOoManagerService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.template.adapters.source.TemplateSourceDocumentAdapterImpl;
import org.nuxeo.template.api.ContentInputType;
import org.nuxeo.template.api.InputType;
import org.nuxeo.template.api.TemplateInput;
import org.nuxeo.template.api.TemplateProcessorService;
import org.nuxeo.template.api.adapters.TemplateBasedDocument;
import org.nuxeo.template.api.adapters.TemplateSourceDocument;

@RunWith(FeaturesRunner.class)
@Features({ TransactionalFeature.class, CoreFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy({ "org.nuxeo.ecm.automation.core", //
        "org.nuxeo.ecm.platform.convert", //
        "org.nuxeo.ecm.platform.preview", //
        "org.nuxeo.ecm.platform.dublincore", //
        "org.nuxeo.template.manager.api", //
        "org.nuxeo.template.manager", //
        "org.nuxeo.template.manager.xdocreport", //
})
public class TestODTProcessingWithConverter {

    @Inject
    protected CoreSession session;

    @Inject
    protected TemplateProcessorService tps;

    @Inject
    protected OOoManagerService oooManagerService;

    private DocumentModel templateDoc;

    private DocumentModel testDoc;

    private static final Log log = LogFactory.getLog(TestODTProcessingWithConverter.class);

    protected static final String TEMPLATE_NAME = "mytestTemplate";

    // @Before
    public void setUp() throws Exception {
        try {
            oooManagerService.startOOoManager();
        } catch (Exception e) {
            log.warn("Can't run OpenOffice, JOD converter will not be available.");
        }

    }

    // @After
    public void tearDown() throws Exception {
        if (oooManagerService.isOOoManagerStarted()) {
            oooManagerService.stopOOoManager();
        }
    }

    protected void setupTestDocs() throws Exception {

        DocumentModel root = session.getRootDocument();

        // create the template
        templateDoc = session.createDocumentModel(root.getPathAsString(), "templatedDoc", "TemplateSource");
        templateDoc.setProperty("dublincore", "title", "MyTemplate");
        File file = FileUtils.getResourceFileFromContext("data/Container.odt");
        Blob fileBlob = Blobs.createBlob(file);
        fileBlob.setFilename("Container.odt");
        templateDoc.setProperty("file", "content", fileBlob);
        templateDoc.setPropertyValue("tmpl:templateName", TEMPLATE_NAME);

        templateDoc = session.createDocument(templateDoc);

        // create the note
        testDoc = session.createDocumentModel(root.getPathAsString(), "testDoc", "Note");
        testDoc.setProperty("dublincore", "title", "MyTestNote2");
        testDoc.setProperty("dublincore", "description", "Simple note sample");

        File mdfile = FileUtils.getResourceFileFromContext("data/MDSample.md");
        Blob mdfileBlob = Blobs.createBlob(mdfile);

        testDoc.setPropertyValue("note:note", mdfileBlob.getString());
        testDoc.setPropertyValue("note:mime_type", "text/x-web-markdown");

        File imgFile = FileUtils.getResourceFileFromContext("data/android.jpg");
        Blob imgBlob = Blobs.createBlob(imgFile);
        imgBlob.setFilename("android.jpg");
        imgBlob.setMimeType("image/jpeg");

        List<Map<String, Serializable>> blobs = new ArrayList<Map<String, Serializable>>();
        Map<String, Serializable> blob1 = new HashMap<String, Serializable>();
        blob1.put("file", (Serializable) imgBlob);
        blob1.put("filename", "android.jpg");
        blobs.add(blob1);

        testDoc.setPropertyValue("files:files", (Serializable) blobs);

        testDoc = session.createDocument(testDoc);
    }

    @Test
    public void testNoteWithMasterTemplateAndConverter() throws Exception {
        assumeTrue("Skipping test since no OOo server can be found", oooManagerService.isOOoManagerStarted());

        setupTestDocs();

        // check the template

        TemplateSourceDocument source = templateDoc.getAdapter(TemplateSourceDocument.class);
        assertNotNull(source);

        // init params
        source.initTemplate(true);

        List<TemplateInput> params = source.getParams();
        // System.out.println(params);
        assertEquals(1, params.size());

        params.get(0).setType(InputType.Content);
        params.get(0).setSource(ContentInputType.HtmlPreview.getValue());

        templateDoc = source.saveParams(params, true);

        // test Converter
        templateDoc.setPropertyValue(TemplateSourceDocumentAdapterImpl.TEMPLATE_OUTPUT_PROP, "pdf");
        templateDoc = session.saveDocument(templateDoc);
        session.save();

        // associate Note to template
        TemplateBasedDocument templateBased = testDoc.getAdapter(TemplateBasedDocument.class);
        assertNull(templateBased);

        testDoc = tps.makeTemplateBasedDocument(testDoc, templateDoc, true);
        templateBased = testDoc.getAdapter(TemplateBasedDocument.class);
        assertNotNull(templateBased);

        // render
        testDoc = templateBased.initializeFromTemplate(TEMPLATE_NAME, true);
        Blob blob = templateBased.renderWithTemplate(TEMPLATE_NAME);
        assertNotNull(blob);

        assertEquals("MyTestNote2.pdf", blob.getFilename());

        ConvertHelper helper = new ConvertHelper();
        Blob txtBlob = helper.convertBlob(blob, "text/plain");
        String txtContent = txtBlob.getString();

        // System.out.println(txtContent);

        assertTrue(txtContent.contains("TemplateBasedDocument"));
        assertTrue(txtContent.contains(testDoc.getTitle()));

    }

}
