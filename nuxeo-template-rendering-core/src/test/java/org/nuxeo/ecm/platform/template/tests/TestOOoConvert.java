package org.nuxeo.ecm.platform.template.tests;

import java.io.File;

import org.junit.Test;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.runtime.api.Framework;

public class TestOOoConvert extends BaseConverterTest {

    protected static final String ODT_MT = "application/vnd.oasis.opendocument.text";

    @Test
    public void testOfficeConverter() throws Exception {
        BlobHolder bh = getBlobFromPath("data/testMe.html", "text/html");

        ConversionService cs = Framework.getLocalService(ConversionService.class);

        String converterName = cs.getConverterName(bh.getBlob().getMimeType(), ODT_MT);
        assertEquals("any2odt", converterName);

        if (cs.isConverterAvailable(converterName).isAvailable()) {

            BlobHolder result = cs.convert(converterName, bh, null);

            File odtFile = File.createTempFile("htmlfile", "odt");

            result.getBlob().transferTo(odtFile);

            bh = getBlobFromPath("data/testMe.md", "text/x-web-markdown");
            assertEquals("any2odt", converterName);

            File odtFile2 = File.createTempFile("mdfile", "odt");

            result = cs.convert(converterName, bh, null);
            result.getBlob().transferTo(odtFile2);

            odtFile.delete();
            odtFile2.delete();
        }

    }

    @Test
    public void testOfficeConverter2() throws Exception {

        BlobHolder bh = getBlobFromPath("data/Spec_ModelNux.odt", "application/vnd.oasis.opendocument.text");

        ConversionService cs = Framework.getLocalService(ConversionService.class);

        String converterName = cs.getConverterName(bh.getBlob().getMimeType(), "application/pdf");
        assertEquals("any2pdf", converterName);

        if (cs.isConverterAvailable(converterName).isAvailable()) {

            BlobHolder result = cs.convert(converterName, bh, null);

            File pdfFile = File.createTempFile("testfile", "pdf");

            result.getBlob().transferTo(pdfFile);

            pdfFile.delete();

        }

    }

}
