// Copyright (c) 2017 ChromaWay Inc. See README for license information.

/*
 * Generated by ASN.1 Java Compiler (http://www.asnlab.org/)
 * From ASN.1 module "Messages"
 */
package net.postchain.ebft.messages;

import java.io.*;
import java.math.*;
import org.asnlab.asndt.runtime.conv.*;
import org.asnlab.asndt.runtime.conv.annotation.*;
import org.asnlab.asndt.runtime.type.AsnType;
import org.asnlab.asndt.runtime.value.*;

public class GetBlockAtHeight {

	@Component(0)
	public Long height;


	public boolean equals(Object obj) {
		if(!(obj instanceof GetBlockAtHeight)){
			return false;
		}
		return TYPE.equals(this, obj, CONV);
	}

	public void der_encode(OutputStream out) throws IOException {
		TYPE.encode(this, EncodingRules.DISTINGUISHED_ENCODING_RULES, CONV, out);
	}

	public static GetBlockAtHeight der_decode(InputStream in) throws IOException {
		return (GetBlockAtHeight)TYPE.decode(in, EncodingRules.DISTINGUISHED_ENCODING_RULES, CONV);
	}


	public final static AsnType TYPE = Messages.type(65541);

	public final static CompositeConverter CONV;

	static {
		CONV = new AnnotationCompositeConverter(GetBlockAtHeight.class);
		AsnConverter heightConverter = LongConverter.INSTANCE;
		CONV.setComponentConverters(new AsnConverter[] { heightConverter });
	}


}
