/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.plugins.signing.type.pgp;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.gradle.internal.UncheckedException;
import org.gradle.plugins.signing.signatory.Signatory;
import org.gradle.plugins.signing.type.AbstractSignatureType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Armored signature type.
 */
public class ArmoredSignatureType extends AbstractSignatureType {

    @Override
    public String getExtension() {
        return "asc";
    }

    @Override
    public void sign(Signatory signatory, InputStream toSign, OutputStream destination) {
        try (OutputStream armoredOutputStream = ArmoredOutputStream.builder().build(destination)) {
            super.sign(signatory, toSign, armoredOutputStream);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
