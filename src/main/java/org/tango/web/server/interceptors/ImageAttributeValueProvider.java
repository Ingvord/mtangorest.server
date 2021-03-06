/*
 * Copyright 2019 Tango Controls
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tango.web.server.interceptors;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.util.Base64;
import org.tango.client.ez.data.type.TangoImage;
import org.tango.client.ez.util.TangoImageUtils;
import org.tango.rest.v10.JaxRsDeviceAttribute;
import org.tango.web.server.binding.EmbeddedImage;
import org.tango.web.server.proxy.TangoAttributeProxy;

import javax.annotation.Priority;
import javax.imageio.ImageIO;
import javax.ws.rs.Priorities;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Optional;

/**
 * @author ingvord
 * @since 11/18/18
 */
@Provider
@EmbeddedImage
@Priority(Priorities.USER + 400)
public class ImageAttributeValueProvider implements MessageBodyWriter<JaxRsDeviceAttribute.ImageAttributeValue>,
        ContainerRequestFilter,
        ContainerResponseFilter {
    @Override
    public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
        return aClass == JaxRsDeviceAttribute.ImageAttributeValue.class &&
                mediaType.getType().equalsIgnoreCase("image") &&
                mediaType.getSubtype().equalsIgnoreCase("jpeg");
    }

    @Override
    public void writeTo(JaxRsDeviceAttribute.ImageAttributeValue image, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> multivaluedMap, OutputStream outputStream) throws IOException, WebApplicationException {
        TangoImage value = image.value;
        RenderedImage img = TangoImageUtils.toRenderedImage_sRGB(
                (int[]) value.getData(), value.getWidth(), value.getHeight());
        outputStream.write("data:/jpeg;base64,".getBytes());
        OutputStream out = new Base64.OutputStream(outputStream);
        ImageIO.write(img, "jpeg", out);

        outputStream.flush();
    }

    @Override
    public void filter(ContainerRequestContext containerRequestContext, ContainerResponseContext containerResponseContext) throws IOException {
        Optional.ofNullable(containerResponseContext.getMediaType())
                .ifPresent(mediaType -> containerResponseContext.getHeaders()
                        .putSingle("Content-Disposition", "inline"));
    }

    @Override
    public void filter(ContainerRequestContext containerRequestContext) throws IOException {
        TangoAttributeProxy attributeProxy = ResteasyProviderFactory.getContextData(TangoAttributeProxy.class);
        if(!attributeProxy.isImage())
            containerRequestContext.abortWith(
                    Response.status(Response.Status.BAD_REQUEST).build());
    }
}
