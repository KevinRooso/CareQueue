package com.carequeue.imports.service;

import com.carequeue.common.error.InvalidMessageException;
import com.carequeue.imports.api.ImportReferralRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferralMessageParserTest {

	private final ReferralMessageParser parser = new ReferralMessageParser(JsonMapper.shared());

	private static final String VALID_JSON = """
			{"externalRef":"GP-2026-000481","referringGp":"Dr Synthetic","clinicCode":"GASTRO",
			 "patient":{"mrn":"MRN-000481","firstName":"Sam","lastName":"Test","dateOfBirth":"1968-04-12"},
			 "clinicalNotes":"synthetic notes"}""";

	private static final String VALID_XML = """
			<?xml version="1.0" encoding="UTF-8"?>
			<referral externalRef="GP-2026-000482">
			  <referringGp>Dr. C. Walsh, Harbour Family Practice</referringGp>
			  <clinicCode>DERMATOLOGY</clinicCode>
			  <patient mrn="MRN-000482">
			    <firstName>Alex</firstName><lastName>Sample</lastName><dateOfBirth>1990-09-30</dateOfBirth>
			  </patient>
			  <clinicalNotes>Long-standing mild eczema.</clinicalNotes>
			</referral>""";

	@Test
	void parseJson_whenValid_returnsRequest() {
		ImportReferralRequest r = parser.parseJson(VALID_JSON);

		assertThat(r.externalRef()).isEqualTo("GP-2026-000481");
		assertThat(r.patient().mrn()).isEqualTo("MRN-000481");
		assertThat(r.patient().dateOfBirth()).isEqualTo(LocalDate.of(1968, 4, 12));
		assertThat(r.clinicCode()).isEqualTo("GASTRO");
	}

	@Test
	void parseJson_whenMalformed_throwsInvalidMessage() {
		assertThatThrownBy(() -> parser.parseJson("{\"externalRef\":"))
				.isInstanceOf(InvalidMessageException.class);
	}

	@Test
	void parseXml_whenValid_returnsRequest() {
		ImportReferralRequest r = parser.parseXml(VALID_XML);

		assertThat(r.externalRef()).isEqualTo("GP-2026-000482");
		assertThat(r.referringGp()).isEqualTo("Dr. C. Walsh, Harbour Family Practice");
		assertThat(r.patient().mrn()).isEqualTo("MRN-000482");
		assertThat(r.patient().dateOfBirth()).isEqualTo(LocalDate.of(1990, 9, 30));
		assertThat(r.clinicalNotes()).isEqualTo("Long-standing mild eczema.");
	}

	@Test
	void parseXml_whenViolatingSchema_throwsInvalidMessage() {
		// missing <clinicCode> and an unclosed <patient>
		String invalid = """
				<?xml version="1.0" encoding="UTF-8"?>
				<referral externalRef="GP-2026-000483">
				  <referringGp>Dr. P. Kelly</referringGp>
				  <patient mrn="MRN-000483">
				    <firstName>Jo</firstName>
				  <clinicalNotes>Test of a malformed message.</clinicalNotes>
				</referral>""";

		assertThatThrownBy(() -> parser.parseXml(invalid))
				.isInstanceOf(InvalidMessageException.class);
	}

	@Test
	void parseXml_whenMissingRequiredElement_throwsInvalidMessage() {
		String missing = """
				<?xml version="1.0" encoding="UTF-8"?>
				<referral externalRef="GP-2026-000484">
				  <referringGp>Dr X</referringGp>
				  <patient mrn="MRN-000484">
				    <firstName>A</firstName><lastName>B</lastName><dateOfBirth>1990-01-01</dateOfBirth>
				  </patient>
				  <clinicalNotes>notes</clinicalNotes>
				</referral>""";

		assertThatThrownBy(() -> parser.parseXml(missing))
				.isInstanceOf(InvalidMessageException.class);
	}

	@Test
	void parseXml_whenXxe_isRejectedWithoutExpandingEntities() {
		String xxe = """
				<?xml version="1.0"?>
				<!DOCTYPE referral [
				  <!ENTITY xxe SYSTEM "file:///etc/passwd">
				]>
				<referral externalRef="GP-2026-000485">
				  <referringGp>&xxe;</referringGp>
				  <clinicCode>GASTRO</clinicCode>
				  <patient mrn="MRN-000485">
				    <firstName>A</firstName><lastName>B</lastName><dateOfBirth>1990-01-01</dateOfBirth>
				  </patient>
				  <clinicalNotes>notes</clinicalNotes>
				</referral>""";

		// DOCTYPE is disallowed entirely → parse error, and the entity is never read.
		assertThatThrownBy(() -> parser.parseXml(xxe))
				.isInstanceOf(InvalidMessageException.class);
	}
}
