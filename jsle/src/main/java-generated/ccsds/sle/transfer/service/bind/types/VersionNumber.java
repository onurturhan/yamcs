/**
 * This class file was automatically generated by jASN1 v1.11.2 (http://www.beanit.com)
 */

package ccsds.sle.transfer.service.bind.types;

import java.io.IOException;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.io.Serializable;
import com.beanit.jasn1.ber.*;
import com.beanit.jasn1.ber.types.*;
import com.beanit.jasn1.ber.types.string.*;

import ccsds.sle.transfer.service.common.types.Credentials;
import ccsds.sle.transfer.service.common.types.IntPosShort;
import ccsds.sle.transfer.service.service.instance.id.ServiceInstanceIdentifier;

public class VersionNumber extends IntPosShort {

	private static final long serialVersionUID = 1L;

	public VersionNumber() {
	}

	public VersionNumber(byte[] code) {
		super(code);
	}

	public VersionNumber(BigInteger value) {
		super(value);
	}

	public VersionNumber(long value) {
		super(value);
	}

}
