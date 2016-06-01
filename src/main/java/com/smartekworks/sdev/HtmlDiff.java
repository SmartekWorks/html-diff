package com.smartekworks.sdev;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONArray;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;

public class HtmlDiff {
	final static int BUFFER_SIZE = 4096;

	public static void main(String[] args) throws Exception{
		// get properties
		String srcZipPath = System.getProperties().getProperty("srcZip");
		String dstZipPath = System.getProperties().getProperty("dstZip");
		String charset = System.getProperties().getProperty("charset");
		if (charset == null) {
			charset = "UTF-8";
		}
		if (srcZipPath == null || dstZipPath == null) {
			System.out.println("Both srcZip and dstZip are required!");
			System.exit(0);
		}

		// read zip files
		File srcZip = new File(srcZipPath);
		File dstZip = new File(dstZipPath);
		if (!srcZip.exists() || !dstZip.exists()) {
			System.out.println("Both srcZip and dstZip are required!");
			System.exit(0);
		}

		// handle filter file
		String domFilterPath = System.getProperties().getProperty("domFilter");
		ArrayList<String> filters = new ArrayList<String>();
		if (domFilterPath != null) {
			File domFilter = new File(domFilterPath);
			if (domFilter.exists()) {
				JSONArray list = new JSONArray(FileUtils.readFileToString(domFilter, charset));
				for (int i = 0; i < list.length(); i++) {
					filters.add(list.getString(i));
				}
			}
		}

		// get zipEntry and compare
		HashMap<String, ArrayList<HashMap<String, String>>> result = compareZipFile(srcZip, dstZip, charset, filters);

		// modify template file
		createResult(System.getProperties().getProperty("outputFolder"), result, charset);

		System.out.println("Generate complete.");
	}

	private static HashMap<String, ArrayList<HashMap<String, String>>> compareZipFile
			(File srcZip, File dstZip, String charset, ArrayList<String> filters) throws IOException{
		HashMap<String, ArrayList<HashMap<String, String>>> result = new HashMap<String, ArrayList<HashMap<String, String>>>();
		ZipFile srcZipFile = new ZipFile(srcZip);
		ZipFile dstZipFile = new ZipFile(dstZip);

		Enumeration<ZipArchiveEntry> srcEntries = srcZipFile.getEntries();
		while (srcEntries.hasMoreElements()) {
			ZipArchiveEntry srcEntry = srcEntries.nextElement();
			if (srcEntry.isDirectory()) {
				ArrayList<HashMap<String, String>> testCase = new ArrayList<HashMap<String, String>>();
				ArrayList<String> nameList = new ArrayList<String>();

				Iterable<ZipArchiveEntry> entries = getEntriesByFolder(srcZipFile, srcEntry.getName());
				for (ZipArchiveEntry entry : entries) {
					HashMap<String, String> operation = new HashMap<String, String>();
					String[] opNames = entry.getName().split("/");
					operation.put("name", opNames[opNames.length - 1].replace(".html", ""));
					nameList.add(entry.getName());

					String srcHtml = readZipEntryToString(srcZipFile, entry, charset);

					ZipArchiveEntry dstEntry = dstZipFile.getEntry(entry.getName());
					if (dstEntry == null) {
						operation.put("src", "");
					} else {
						String dstHtml = readZipEntryToString(dstZipFile, dstEntry, charset);
						if (!compareHtmlFile(srcHtml, dstHtml, filters)) {
							operation.put("src", srcHtml);
							operation.put("dst", dstHtml);
						}
					}

					testCase.add(operation);
				}

				entries = getEntriesByFolder(dstZipFile, srcEntry.getName());
				for (ZipArchiveEntry entry : entries) {
					if (!nameList.contains(entry.getName())) {
						HashMap<String, String> operation = new HashMap<String, String>();
						String[] names = entry.getName().split("/");
						operation.put("name", names[names.length - 1].replace(".html", ""));
						operation.put("dst", "");
						testCase.add(operation);
					}
				}

				String caseName = srcEntry.getName().replace("/", "");
				result.put(caseName, testCase);
			}
		}

		Enumeration<ZipArchiveEntry> dstEntries = dstZipFile.getEntries();
		while (dstEntries.hasMoreElements()) {
			ZipArchiveEntry dstEntry = dstEntries.nextElement();
			if (dstEntry.isDirectory()) {
				ArrayList<HashMap<String, String>> testCase = new ArrayList<HashMap<String, String>>();
				String caseName = dstEntry.getName().replace("/", "");
				if (!result.containsKey(caseName)) {
					Iterable<ZipArchiveEntry> entries = getEntriesByFolder(dstZipFile, dstEntry.getName());
					for (ZipArchiveEntry entry : entries) {
						if (!entry.isDirectory()) {
							HashMap<String, String> op = new HashMap<String, String>();
							String[] names = entry.getName().split("/");
							op.put("name", names[names.length - 1].replace(".html", ""));
							op.put("dst", "");
							testCase.add(op);
						}
					}
					result.put(caseName, testCase);
				}
			}
		}

		srcZipFile.close();
		dstZipFile.close();

		return result;
	}

	private static ArrayList<ZipArchiveEntry> getEntriesByFolder(ZipFile zipFile, String folder) {
		ArrayList<ZipArchiveEntry> entryList = new ArrayList<ZipArchiveEntry>();

		Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
		while (entries.hasMoreElements()) {
			ZipArchiveEntry entry = entries.nextElement();
			if (!entry.isDirectory() && entry.getName().startsWith(folder)) {
				entryList.add(entry);
			}
		}

		return entryList;
	}

	private static String readZipEntryToString(ZipFile zipFile, ZipArchiveEntry entry, String charset) throws IOException{
		InputStream is = zipFile.getInputStream(entry);

		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		byte[] bytes = new byte[BUFFER_SIZE];
		int count;
		while((count = is.read(bytes,0,BUFFER_SIZE)) != -1)
			outStream.write(bytes, 0, count);
		is.close();

		return new String(outStream.toByteArray(), charset);
	}

	private static boolean compareHtmlFile(String src, String dst, ArrayList<String> filters) {

		if (filters.size() > 0) {
			String srcLower = src.toLowerCase();
			String dstLower = dst.toLowerCase();

			ArrayList<String> srcList = new ArrayList<String>();
			ArrayList<String> dstList = new ArrayList<String>();

			int startIndex = 0;
			while (true) {
				startIndex = srcLower.indexOf("<html", startIndex);
				if (startIndex < 0) {
					break;
				}

				int endIndex = srcLower.indexOf("</html>", startIndex) + "</html>".length();
				srcList.add(src.substring(startIndex, endIndex));
				startIndex = endIndex;
			}

			startIndex = 0;
			while (true) {
				startIndex = dstLower.indexOf("<html", startIndex);
				if (startIndex < 0) {
					break;
				}

				int endIndex = dstLower.indexOf("</html>", startIndex) + "</html>".length();
				dstList.add(dst.substring(startIndex, endIndex));
				startIndex = endIndex;
			}

			if (srcList.size() != dstList.size())
				return false;

			for (int i = 0; i < srcList.size(); i++) {
				String srcTemp = srcList.get(i);
				String dstTemp = dstList.get(i);

				Document srcDoc = Jsoup.parse(srcTemp);
				Document dstDoc = Jsoup.parse(dstTemp);

				for (String selector : filters) {
					Elements elements = srcDoc.select(selector);
					elements.remove();
					elements = dstDoc.select(selector);
					elements.remove();
				}

				if (!srcDoc.body().outerHtml().equals(dstDoc.body().outerHtml()))
					return false;
			}

			return true;
		} else {
			return src.equals(dst);
		}
	}

	private static void createResult(String outputPath, HashMap<String, ArrayList<HashMap<String, String>>> result, String charset) throws IOException{
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
		String timestamp = sdf.format(new Date());

		if (outputPath != null) {
			if (!outputPath.endsWith(System.getProperty("file.separator"))) {
				outputPath += System.getProperty("file.separator");
			}
		} else {
			outputPath = "result" + System.getProperty("file.separator");
		}

		File templateFolder = new File("template" + System.getProperty("file.separator"));
		File resultFolder = new File(outputPath + timestamp + System.getProperty("file.separator"));
		FileUtils.forceMkdir(resultFolder);

		File caTemplateFile = new File(templateFolder, "cases.html");
		String casesLinks = "";

		ArrayList<String> keyList = new ArrayList<String>(result.keySet());
		Collections.sort(keyList, new Comparator<String>() {
			public int compare(String s1, String s2) {
				return s1.compareTo(s2);
			}
		});

		int caseIndex = 0;
		for (String key : keyList) {
			File caseTemplate = new File(templateFolder, "case" + System.getProperty("file.separator"));
			File evTemplateFile = new File(caseTemplate, "evidences.html");
			File opTemplateFile = new File(caseTemplate, "operation.html");

			File caseFolder = new File(resultFolder, "" + caseIndex + System.getProperty("file.separator"));
			FileUtils.forceMkdir(caseFolder);

			ArrayList<HashMap<String, String>> operations = result.get(key);
			String operationLinks = "";
			boolean different = false;
			boolean srcOnly = true;
			boolean dstOnly = true;
			if (operations.size() == 0) {
				srcOnly = false;
				dstOnly = false;
			} else {
				for (HashMap<String, String> operation : operations) {
					if (operation.containsKey("src") && operation.containsKey("dst")) {
						operationLinks = operationLinks + "<li><a href=\"" + operation.get("name") + ".html\" target=\"diffFrame\">" +
								operation.get("name") + "</a></li>"+ System.getProperty("line.separator");
						different = true;
						srcOnly = false;
						dstOnly = false;

						String opTemplateHtml = FileUtils.readFileToString(opTemplateFile);
						opTemplateHtml = opTemplateHtml.replace("{OPERATION_CODE}", key + "-" + operation.get("name"))
								.replace("{CHARSET}", charset)
								.replace("{SRC_HTML}", StringEscapeUtils.escapeHtml4(operation.get("src")))
								.replace("{DST_HTML}", StringEscapeUtils.escapeHtml4(operation.get("dst")));
						File operationFile = new File(caseFolder, operation.get("name") + ".html");
						FileUtils.writeStringToFile(operationFile, opTemplateHtml, charset);
					} else if (operation.containsKey("src")) {
						operationLinks = operationLinks + "<li><a href=\"javascript:void(0)\" target=\"diffFrame\">" +
								operation.get("name") + " (SRC Only)</a></li>"+ System.getProperty("line.separator");
						different = true;
						dstOnly = false;
					} else if (operation.containsKey("dst")) {
						operationLinks = operationLinks + "<li><a href=\"javascript:void(0)\" target=\"diffFrame\">" +
								operation.get("name") + " (DST Only)</a></li>"+ System.getProperty("line.separator");
						different = true;
						srcOnly = false;
					} else {
						operationLinks = operationLinks + "<li><a href=\"javascript:void(0)\" class=\"muted\" target=\"diffFrame\">" +
								operation.get("name") + " (No Differences)</a></li>"+ System.getProperty("line.separator");
						srcOnly = false;
						dstOnly = false;
					}
				}
			}

			String evTemplateHtml = FileUtils.readFileToString(evTemplateFile);
			evTemplateHtml = evTemplateHtml.replace("{CASE_CODE}", key).replace("{OPERATION_LINKS}", operationLinks);
			File evidencesFile = new File(caseFolder, "evidences.html");
			FileUtils.writeStringToFile(evidencesFile, evTemplateHtml);

			if (srcOnly) {
				casesLinks = casesLinks + "<li><a href=\"" + caseIndex + "/evidences.html\" target=\"evidencesFrame\">" +
						key + " (SRC Only)</a></li>"+ System.getProperty("line.separator");
			} else if (dstOnly) {
				casesLinks = casesLinks + "<li><a href=\"" + caseIndex + "/evidences.html\" target=\"evidencesFrame\">" +
						key + " (DST Only)</a></li>"+ System.getProperty("line.separator");
			} else if (different) {
				casesLinks = casesLinks + "<li><a href=\"" + caseIndex + "/evidences.html\" target=\"evidencesFrame\">" +
						key + "</a></li>"+ System.getProperty("line.separator");
			} else {
				casesLinks = casesLinks + "<li><a href=\"" + caseIndex + "/evidences.html\" class=\"muted\" target=\"evidencesFrame\">" +
						key + " (No Differences)</a></li>"+ System.getProperty("line.separator");
			}

			caseIndex++;
		}

		String caTemplateHtml = FileUtils.readFileToString(caTemplateFile);
		caTemplateHtml = caTemplateHtml.replace("{CASE_LINKS}", casesLinks);
		File casesFile = new File(resultFolder, "cases.html");
		FileUtils.writeStringToFile(casesFile, caTemplateHtml);

		// copy other files
		File index = new File(templateFolder, "index.html");
		File nodiff = new File(templateFolder, "nodifferences.html");
		File noevi = new File(templateFolder, "noevidences.html");
		File stylesheet = new File(templateFolder, "stylesheet.css");
		File prettydiff = new File(templateFolder, "prettydiff.js");
		FileUtils.copyFileToDirectory(index, resultFolder);
		FileUtils.copyFileToDirectory(nodiff, resultFolder);
		FileUtils.copyFileToDirectory(noevi, resultFolder);
		FileUtils.copyFileToDirectory(stylesheet, resultFolder);
		FileUtils.copyFileToDirectory(prettydiff, resultFolder);
	}
}
