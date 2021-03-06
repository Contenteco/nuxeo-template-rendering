package org.nuxeo.template.processors.fm;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeRegistry;
import org.nuxeo.ecm.platform.rendering.fm.FreemarkerEngine;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.template.api.TemplateInput;
import org.nuxeo.template.api.TemplateProcessor;
import org.nuxeo.template.api.adapters.TemplateBasedDocument;
import org.nuxeo.template.fm.FMContextBuilder;
import org.nuxeo.template.fm.FreeMarkerVariableExtractor;
import org.nuxeo.template.processors.AbstractTemplateProcessor;

import freemarker.cache.StringTemplateLoader;

public class FreeMarkerProcessor extends AbstractTemplateProcessor implements TemplateProcessor {

    protected StringTemplateLoader loader = new StringTemplateLoader();

    protected FreemarkerEngine fmEngine = null;

    protected FMContextBuilder fmContextBuilder = new FMContextBuilder();

    protected FreemarkerEngine getEngine() {
        if (fmEngine == null) {
            fmEngine = new FreemarkerEngine();
            fmEngine.getConfiguration().setTemplateLoader(loader);
        }
        return fmEngine;
    }

    protected final static Pattern XMLStartPattern = Pattern.compile("<\\?xml");

    protected final static Pattern HtmlTagPattern = Pattern.compile("<(\\S+?)(.*?)>(.*?)</\\1>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE);

    protected String guessMimeType(Blob result, MimetypeRegistry mreg) throws Exception {

        if (result == null) {
            return null;
        }

        String content = result.getString();

        if (XMLStartPattern.matcher(content).find()) {
            return "text/xml";
        }

        if (HtmlTagPattern.matcher(content).find()) {
            return "text/html";
        }

        return mreg.getMimetypeFromBlobWithDefault(result, "text/plain");
    }

    protected void setBlobAttributes(Blob result, TemplateBasedDocument templateBasedDocument) throws Exception {

        // try to guess mimetype and extension of the resulting Blob

        MimetypeRegistry mreg = Framework.getLocalService(MimetypeRegistry.class);

        String mimetype = "text/html";
        String extension = ".html";

        if (mreg != null) {
            String found_mimetype = guessMimeType(result, mreg);
            if (found_mimetype != null) {
                mimetype = found_mimetype;
                List<String> extensions = mreg.getExtensionsFromMimetypeName(mimetype);
                if (extensions != null && extensions.size() > 0) {
                    extension = "." + extensions.get(0);
                }
            }
        }
        if ("text/xml".equalsIgnoreCase(mimetype)) {
            // because MimetypeRegistry return a stupid result for XML
            extension = ".xml";
        }
        result.setMimeType(mimetype);
        String targetFileName = FileUtils.getFileNameNoExt(templateBasedDocument.getAdaptedDoc().getTitle());
        result.setFilename(targetFileName + extension);
    }

    @Override
    public Blob renderTemplate(TemplateBasedDocument templateBasedDocument, String templateName) throws Exception {

        Blob sourceTemplateBlob = getSourceTemplateBlob(templateBasedDocument, templateName);

        String fmTemplateKey = "main" + System.currentTimeMillis();

        String ftl = sourceTemplateBlob.getString();

        loader.putTemplate(fmTemplateKey, ftl);

        Map<String, Object> ctx = fmContextBuilder.build(templateBasedDocument, templateName);

        FMBindingResolver resolver = new FMBindingResolver();
        resolver.resolve(templateBasedDocument.getParams(templateName), ctx, templateBasedDocument);

        StringWriter writer = new StringWriter();
        getEngine().render(fmTemplateKey, ctx, writer);

        Blob result = Blobs.createBlob(writer.toString());
        setBlobAttributes(result, templateBasedDocument);

        return result;
    }

    @Override
    public List<TemplateInput> getInitialParametersDefinition(Blob blob) throws Exception {
        List<TemplateInput> params = new ArrayList<TemplateInput>();

        if (blob != null) {
            String xmlContent = blob.getString();

            if (xmlContent != null) {
                List<String> vars = FreeMarkerVariableExtractor.extractVariables(xmlContent);

                for (String var : vars) {
                    TemplateInput input = new TemplateInput(var);
                    params.add(input);
                }
            }
        }
        return params;
    }

}
