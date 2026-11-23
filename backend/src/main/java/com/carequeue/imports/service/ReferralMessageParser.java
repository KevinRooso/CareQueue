package com.carequeue.imports.service;

import com.carequeue.common.error.InvalidMessageException;
import com.carequeue.imports.api.ImportReferralRequest;
import com.carequeue.imports.api.ImportReferralRequest.ImportPatient;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import tools.jackson.databind.ObjectMapper;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.StringReader;
import java.time.LocalDate;

/**
 * Turns a raw import payload into an {@link ImportReferralRequest}.
 *
 * <p>JSON goes through the app's Jackson 3 mapper; XML through the JDK's DOM parser —
 * deliberately NOT jackson-dataformat-xml, which would also register an XML message
 * converter and make the API answer {@code Accept: application/xml} with XML
 * (ADR-010). The DOM parser and the {@code xsd/referral.xsd} validator are hardened
 * against XXE: no DOCTYPE, secure processing, no external DTD/schema access.
 */
@Component
public class ReferralMessageParser {

	private final ObjectMapper objectMapper;
	private final Schema referralSchema;

	public ReferralMessageParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		try {
			SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			this.referralSchema = factory.newSchema(
					new javax.xml.transform.stream.StreamSource(
							getClass().getResourceAsStream("/xsd/referral.xsd")));
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot load referral XSD", e);
		}
	}

	public ImportReferralRequest parseJson(String payload) {
		try {
			return objectMapper.readValue(payload, ImportReferralRequest.class);
		}
		catch (Exception e) {
			throw new InvalidMessageException("The JSON message could not be parsed.");
		}
	}

	public ImportReferralRequest parseXml(String payload) {
		Document document;
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setExpandEntityReferences(false);
			factory.setXIncludeAware(false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			DocumentBuilder builder = factory.newDocumentBuilder();
			document = builder.parse(new InputSource(new StringReader(payload)));
		}
		catch (SAXParseException e) {
			throw new InvalidMessageException("The XML message is not well-formed.");
		}
		catch (Exception e) {
			throw new InvalidMessageException("The XML message could not be parsed.");
		}

		try {
			Validator validator = referralSchema.newValidator();
			validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			validator.validate(new DOMSource(document));
		}
		catch (Exception e) {
			// SAX messages can echo payload content — keep the detail generic (never log the payload).
			throw new InvalidMessageException(
					"The XML message does not match the referral schema.");
		}

		Element root = document.getDocumentElement();
		Element patient = single(root, "patient");
		LocalDate dob;
		try {
			// xs:date may carry a timezone offset that LocalDate can't hold.
			dob = LocalDate.parse(text(patient, "dateOfBirth"));
		}
		catch (Exception e) {
			throw new InvalidMessageException("The patient dateOfBirth must be a plain date (YYYY-MM-DD).");
		}
		return new ImportReferralRequest(
				root.getAttribute("externalRef"),
				text(root, "referringGp"),
				text(root, "clinicCode"),
				new ImportPatient(
						patient.getAttribute("mrn"),
						text(patient, "firstName"),
						text(patient, "lastName"),
						dob),
				text(root, "clinicalNotes"));
	}

	private static Element single(Element parent, String name) {
		NodeList nodes = parent.getElementsByTagName(name);
		if (nodes.getLength() != 1) {
			throw new InvalidMessageException("The XML message has an unexpected structure.");
		}
		return (Element) nodes.item(0);
	}

	private static String text(Element parent, String name) {
		return single(parent, name).getTextContent().strip();
	}
}
