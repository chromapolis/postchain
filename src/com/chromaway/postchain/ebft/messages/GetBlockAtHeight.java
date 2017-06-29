/*
 * Generated by ASN.1 Java Compiler (http://www.asnlab.org/)
 * From ASN.1 module "com.chromaway.postchain.ebft.messages"
 */
package com.chromaway.postchain.ebft.messages;

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

	public void ber_encode(OutputStream out) throws IOException {
		TYPE.encode(this, EncodingRules.BASIC_ENCODING_RULES, CONV, out);
	}

	public static GetBlockAtHeight ber_decode(InputStream in) throws IOException {
		return (GetBlockAtHeight)TYPE.decode(in, EncodingRules.BASIC_ENCODING_RULES, CONV);
	}


	public final static AsnType TYPE = Messages.type(65540);

	public final static CompositeConverter CONV;

	static {
		CONV = new AnnotationCompositeConverter(GetBlockAtHeight.class);
		AsnConverter heightConverter = LongConverter.INSTANCE;
		CONV.setComponentConverters(new AsnConverter[] { heightConverter });
	}


}
