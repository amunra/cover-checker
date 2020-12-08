/*
	Copyright 2018 NAVER Corp.

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
 */
package com.naver.nid.cover.cobertura;

import com.naver.nid.cover.parser.coverage.CoverageReportXmlHandler;
import com.naver.nid.cover.parser.coverage.model.CoverageStatus;
import com.naver.nid.cover.parser.coverage.model.FileCoverageReport;
import com.naver.nid.cover.parser.coverage.model.LineCoverageReport;
import lombok.extern.slf4j.Slf4j;
import org.xml.sax.Attributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * cobertura report를 파싱하는 sax 핸들러
 * non thread safe
 */
@Slf4j
public class CoberturaCoverageReportHandler extends CoverageReportXmlHandler {

	private static final String ATTR_FILENAME = "filename";
	private static final String ATTR_HITS = "hits";
	private static final String ATTR_BRANCH = "branch";
	private static final String ATTR_CONDITION_COVERAGE = "condition-coverage";

	private static final String TAG_CLASS = "class";
	private static final String TAG_METHOD = "method";
	private static final String TAG_LINE = "line";

	private boolean isNowMethod; // 현재 메소드 내부인지

	private Map<String, FileCoverageReport> reports = new HashMap<>();
	private List<LineCoverageReport> lineReports;
	private FileCoverageReport current;

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) {
		if (localName.equals("")) localName = qName;
		if (TAG_CLASS.equals(localName)) {
			initClass(attributes);
		} else if (TAG_METHOD.equals(localName)) {
			isNowMethod = true;
		} else if (TAG_LINE.equals(localName)) {
			initLine(attributes);
		}
	}

	@Override
	public void endElement(String uri, String localName, String qName) {
		if (localName.equals("")) localName = qName;
		if (TAG_METHOD.equals(localName)) {
			isNowMethod = false;
		} else if (TAG_CLASS.equals(localName)) {
			reports.put(current.getFileName(), current);
			current = null;
			lineReports = null;
		}
	}

	private void initLine(Attributes attributes) {
		if (isNowMethod) return;

		LineCoverageReport lineCoverageReport = new LineCoverageReport();
		lineCoverageReport.setStatus(getCoverageStatus(attributes));
		lineCoverageReport.setLineNum(Integer.parseInt(attributes.getValue("number")));
		lineCoverageReport.setLineContent("");
		log.debug("parse line {}", lineCoverageReport);
		lineReports.add(lineCoverageReport);
	}

	/**
	 * 라인의 상태에 따라 현재 라인의 테스트 커버 유무를 정한다.
	 *
	 * @param attributes
	 * @return
	 */
	private CoverageStatus getCoverageStatus(Attributes attributes) {
		int hits = Integer.parseInt(attributes.getValue(ATTR_HITS));
		boolean branch = Boolean.parseBoolean(attributes.getValue(ATTR_BRANCH));

		if (hits <= 0) return CoverageStatus.UNCOVERED;
		if (!branch || attributes.getValue(ATTR_CONDITION_COVERAGE).startsWith("100%")) return CoverageStatus.COVERED;
		return CoverageStatus.CONDITION;
	}

	/**
	 * class 태그 초기화
	 *
	 * @param attributes
	 */
	private void initClass(Attributes attributes) {
		String fileName = attributes.getValue(ATTR_FILENAME);
		if(log.isDebugEnabled()) {
			log.debug("parse class {}({})", attributes.getValue("name"), fileName);
		}
		if (reports.containsKey(fileName)) {
			current = reports.get(fileName);
			lineReports = current.getLineCoverageReportList();
		} else {
			current = new FileCoverageReport();
			lineReports = new ArrayList<>();

			current.setFileName(fileName);
			current.setType(getFileType(fileName));
			current.setLineCoverageReportList(lineReports);
		}
	}

	/**
	 * extract file extension
	 * @param fileName file name
	 * @return
	 */
	private String getFileType(String fileName) {
		String[] split = fileName.split("\\.");
		if (split.length > 1) {
			return split[split.length - 1];
		} else {
			return "";
		}
	}

	@Override
	public List<FileCoverageReport> getReports() {
		return new ArrayList<>(reports.values());
	}
}
