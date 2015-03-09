package org.tango.web.server.rest;

import com.google.gson.Gson;
import org.javatuples.Triplet;
import org.jboss.resteasy.util.Base64;
import org.junit.Test;
import org.tango.client.ez.attribute.Quality;
import org.tango.client.ez.data.type.TangoImage;
import org.tango.client.ez.proxy.TangoProxy;
import org.tango.client.ez.util.TangoImageUtils;
import org.tango.web.rest.Response;

import javax.imageio.ImageIO;
import java.awt.image.RenderedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ImageAttributeHelperTest {

    @Test
    public void testSend() throws Exception {
        TangoProxy proxy = mock(TangoProxy.class);

        long now = System.currentTimeMillis();

        //fake TangoImage - single black pixel image
        TangoImage<int[]> tangoImage = new TangoImage<>(new int[]{0}, 1, 1); when(proxy.<TangoImage<int[]>>readAttributeValueTimeQuality(any(String.class)))
                .thenReturn(new Triplet<>(tangoImage, now, Quality.VALID));

        Rest2Tango.ImageAttributeHelper instance = new Rest2Tango.ImageAttributeHelper(proxy,System.getProperty("java.io.tmpdir"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        instance.send("any",baos);

        //prepare expected
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        java.io.OutputStream encoded = new Base64.OutputStream(new BufferedOutputStream(out));
        RenderedImage renderedImage = TangoImageUtils.toRenderedImage_sRGB(tangoImage.data, tangoImage.width, tangoImage.height);
        ImageIO.write(renderedImage,"jpeg", Files.createTempFile("ImageAttributeHelperTest_testSend_", ".jpeg").toFile());
        ImageIO.write(renderedImage,"jpeg", encoded);

        Response<String> expectedResponse = new Response<String>("data:/jpeg;base64," + out.toString("UTF-8"),null,Quality.VALID.name(),now);

        Gson gson = new Gson();

        //simulate JsonResponseWriter
        String expected = gson.toJson(expectedResponse);

        String actual = baos.toString("UTF-8");

        assertEquals(expected, actual);
    }
}