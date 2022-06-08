/**
 * This class file was automatically generated by jASN1 v1.11.2 (http://www.beanit.com)
 */

package ccsds.sle.transfer.service.rocf.structures;

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

import ccsds.sle.transfer.service.common.pdus.ReportingCycle;
import ccsds.sle.transfer.service.common.types.DeliveryMode;
import ccsds.sle.transfer.service.common.types.Diagnostics;
import ccsds.sle.transfer.service.common.types.IntPosShort;
import ccsds.sle.transfer.service.common.types.ParameterName;
import ccsds.sle.transfer.service.common.types.Time;

public class ControlWordTypeNumber extends RequestedControlWordTypeNumber {

	private static final long serialVersionUID = 1L;

	public ControlWordTypeNumber() {
	}

	public ControlWordTypeNumber(byte[] code) {
		super(code);
	}

	public ControlWordTypeNumber(BigInteger value) {
		super(value);
	}

	public ControlWordTypeNumber(long value) {
		super(value);
	}

}
