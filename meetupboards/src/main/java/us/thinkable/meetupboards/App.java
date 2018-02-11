package us.thinkable.meetupboards;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

import us.thinkable.xcore.FileUtil;

/**
 * Hello world!
 *
 */
public class App {
	private static String key;
	private static Pattern p1 = Pattern.compile("http(s?://.*?)\\s");
	private static Pattern p2 = Pattern.compile("\\[url=http(s?://.*?)]");
	private static String group;
	private static SimpleDateFormat sdf1 = new SimpleDateFormat("MMM dd, yyyy");
	private static SimpleDateFormat sdf2 = new SimpleDateFormat("MMM dd, yyyy HH:mm");

	public static void main(String[] args) throws IOException {
		group = args[0];
		key = args[1];
		List<Board> boards = getBoards(group);
		boards = sortBoards(boards);
		// dumpBoards(boards);
		htmlBoards(group, boards);

	}

	private static class Board {
		Date created;
		String description;
		String id;
		String name;
		Date updated;
		List<Discussion> discussions;
	};

	private static class Discussion {
		String body;
		Date created;
		String id;
		String subject;
		Date updated;
		List<Post> posts;
	};

	private static class Post {
		String id;
		String inReplyTo;
		String member;
		String subject;
		String body;
		Date created;
		Date updated;
	}

	private static List<Board> getBoards(String group) throws IOException {
		List<Board> result = new ArrayList<Board>();
		try {
			URL url = new URL("https://api.meetup.com/" + group + "/boards?key=" + key + "");
			InputStream stream = url.openStream();
			String s = FileUtil.fileRead(stream);
			JsonReader reader = Json.createReader(new StringReader(s));
			JsonArray jsonArray = reader.readArray();
			for (int i = 0; i < jsonArray.size(); i++) {
				JsonObject obj = jsonArray.getJsonObject(i);
				Board board = new Board();
				board.created = new Date(obj.getJsonNumber("created").longValue());
				board.description = obj.getString("description");
				board.id = obj.getJsonNumber("id").toString();
				board.name = obj.getString("name");
				board.updated = new Date(obj.getJsonNumber("updated").longValue());
				System.out.println("NAME: " + board.name);
				board.discussions = getDiscussions(group, board.id);
				result.add(board);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return result;
	}

	private static String makeFilename(String s) {
		String filename = s.toLowerCase();
		filename = filename.replaceAll("['\" ]", "");
		return filename;
	}

	private static String html(String s) {
		String[] lines = s.split("[\n\r]");
		String result = "";
		String hash = "xv8pv9";

		for (String line : lines) {
			line = line.trim();
			if (line.length() == 0) continue;
			line += " ";
			String orig = line;
			String br = "<p>";
			line = line.replaceAll("\\[list\\]", "<ul>");
			line = line.replaceAll("\\[list=1\\]", "<ol>");
			line = line.replaceAll("\\[/list=1\\]", "</ol>");
			line = line.replaceAll("\\[/list\\]", "</ul>");
			line = line.replaceAll("\\[\\*\\]", "<li>");
			if (!orig.equals(line)) {
				br = "";
			}
			line = line.replaceAll("\\[u\\]", "<u>");
			line = line.replaceAll("\\[/u\\]", "</u>");
			line = line.replaceAll("\\[i\\]", "<i>");
			line = line.replaceAll("\\[/i\\]", "</i>");
			line = line.replaceAll("\\[b\\]", "<b>");
			line = line.replaceAll("\\[/b\\]", "</b>");
			line = line.replaceAll("\\[color=red\\]", "<font color=\"red\">");
			line = line.replaceAll("\\[/color\\]", "</font>");

			// match [url=...]
			Matcher m = p2.matcher(line);
			int pos = 0;
			String newLine = "";
			while (m.find()) {
				String url = m.group(1);
				newLine = line.substring(pos, m.start()) + "<a href=\"" + hash + url + "\">";
				pos = m.end();
			}
			line = newLine + line.substring(pos);
			line = line.replaceAll("\\[/url\\]", "</a>");

			// match http:// and https://
			// but replace all http with xv8pv9 because the next pattern matcher
			// will match the http!
			m = p1.matcher(line);
			pos = 0;
			newLine = "";
			while (m.find()) {
				String url = m.group(1);
				newLine += line.substring(pos, m.start()) + "<a href=\"" + hash + url + "\">" + hash + url + "</a>";
				pos = m.end();
			}
			line = newLine + line.substring(pos);

			// replace all xv8pv9 with http to repair the damage done earlier.
			line = line.replaceAll(hash, "http");

			result += line + br;
		}
		return result;
	}

	private static void htmlBoards(String group, List<Board> boards) {
		File dir = new File(makeFilename(group));
		dir.mkdir();
		File index = new File(dir, "index.html");
		String toc = "<head><body>\n<h1>Table of Contents</h1>\n<ol>";
		for (Board board : boards) {
			toc += "<li>" + sdf1.format(board.created) + ": " + board.name + "</li>\n";
			toc += "<ol>\n";
			board.discussions = sortDiscussions(board.discussions);
			for (Discussion discussion : board.discussions) {
				toc += "<li><a href=\"" + makeFilename(discussion.id) + ".html\">" + sdf1.format(discussion.created)
						+ ": " + discussion.subject + "</a></li>\n";
				htmlDiscussion(dir, board, discussion);
			}
			toc += "</ol>\n";
		}
		toc += "</ol>";
		toc += "</body></html>";
		try {
			FileUtil.fileWrite(index, toc);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void htmlDiscussion(File dir, Board board, Discussion discussion) {
		File file = new File(dir, makeFilename(discussion.id) + ".html");
		String s = "<html><body>";
		s += "<h3>" + discussion.subject + "</h3>\n";
		discussion.posts = getPosts(group, board.id, discussion.id);
		s += htmlPosts(discussion.posts);
		s += "</body></html>";
		try {
			FileUtil.fileWrite(file, s);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static String htmlPosts(List<Post> posts) {
		String s = "";
		for (Post post : posts) {
			s += "<h3>" + post.member + "</h3>\n";
			s += "Created: " + post.created.toString() + "<br>\n";
			s += "Updated: " + post.updated.toString() + "<br>\n";
			s += "<p>" + html(post.body) + "</p>\n";
		}
		return s;
	}

	private static List<Discussion> getDiscussions(String group, String bid) throws IOException {
		List<Discussion> result = new ArrayList<Discussion>();
		try {
			URL url = new URL("https://api.meetup.com/" + group + "/boards/" + bid + "/discussions?key=" + key + "");
			InputStream stream = url.openStream();
			String s = FileUtil.fileRead(stream);
			JsonReader reader = Json.createReader(new StringReader(s));
			JsonArray jsonArray = reader.readArray();
			for (int i = 0; i < jsonArray.size(); i++) {
				JsonObject obj = jsonArray.getJsonObject(i);
				Discussion discussion = new Discussion();
				discussion.body = obj.getString("body");
				discussion.created = new Date(obj.getJsonNumber("created").longValue());
				discussion.id = obj.getJsonNumber("id").toString();
				discussion.subject = obj.getString("subject");
				discussion.updated = new Date(obj.getJsonNumber("updated").longValue());
				System.out.println("\tSUBJECT: " + discussion.subject);
				// System.out.println("\tBODY: " + discussion.body);
				// discussion.posts = getPosts(group, bid, discussion.id);
				result.add(discussion);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return result;
	}

	private static List<Post> getPosts(String group, String bid, String did) {
		List<Post> result = new ArrayList<Post>();
		try {
			URL url = new URL("https://api.meetup.com/" + group + "/boards/" + bid + "/discussions/" + did + "?key="
					+ key + "&sign=true");
			InputStream stream = url.openStream();
			String s = FileUtil.fileRead(stream);
			JsonReader reader = Json.createReader(new StringReader(s));
			JsonArray jsonArray = reader.readArray();
			for (int i = 0; i < jsonArray.size(); i++) {
				JsonObject obj = jsonArray.getJsonObject(i);
				Post post = new Post();
				post.id = obj.getJsonNumber("id").toString();
				post.inReplyTo = obj.getJsonNumber("in_reply_to").toString();
				post.member = obj.getJsonObject("member").getString("name");
				post.subject = obj.getString("subject");
				post.body = obj.getString("body");
				post.updated = new Date(obj.getJsonNumber("updated").longValue());
				post.created = new Date(obj.getJsonNumber("created").longValue());
				System.out.println("\t\tSUBJECT: " + post.subject);
				// System.out.println("\tBODY: " + discussion.body);
				// board.discussions = getDiscussions(group, board.id;
				result.add(post);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			System.exit(0);
		}
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;
	}

	private static void dumpBoards(List<Board> boards) {
		for (Board board : boards) {
			System.out.println("NAME: " + board.name);
			System.out.println("CREATED: " + board.created.toString());
			System.out.println("UPDATED: " + board.updated.toString());
			System.out.println(board.description);
			dumpDiscussions(board.discussions);
		}
	}

	private static void dumpDiscussions(List<Discussion> discussions) {
		for (Discussion discussion : discussions) {
			System.out.println("\tSUBJECT: " + discussion.subject);
			System.out.println("\tCREATED: " + discussion.created.toString());
			System.out.println("\tUPDATED: " + discussion.updated.toString());
			System.out.println("\tBODY: " + discussion.body.toString());
			dumpPosts(discussion.posts);
		}
	}

	private static void dumpPosts(List<Post> posts) {
		for (Post post : posts) {
			System.out.println("\t\tSUBJECT: " + post.subject);
			System.out.println("\t\tMEMBER: " + post.member);
			System.out.println("\t\tCREATED: " + post.created.toString());
			System.out.println("\t\tUPDATED: " + post.updated.toString());
			System.out.println("\t\tBODY: " + post.body);
		}
	}

	private static class BoardSorter implements Comparator<Board> {

		public int compare(Board o1, Board o2) {
			// TODO Auto-generated method stub
			return o1.created.getTime() < o2.created.getTime() ? -1 : 1;
		}

	}

	private static class DiscussionSorter implements Comparator<Discussion> {

		public int compare(Discussion o1, Discussion o2) {
			// TODO Auto-generated method stub
			return o1.created.getTime() < o2.created.getTime() ? -1 : 1;
		}

	}

	private static List<Board> sortBoards(List<Board> boards) {
		Board[] sorted = boards.toArray(new Board[0]);
		Arrays.sort(sorted, new BoardSorter());
		List<Board> result = new ArrayList<Board>();
		for (Board board : sorted) {
			result.add(board);
		}
		return result;
	}

	private static List<Discussion> sortDiscussions(List<Discussion> discussions) {
		Discussion[] sorted = discussions.toArray(new Discussion[0]);
		Arrays.sort(sorted, new DiscussionSorter());
		List<Discussion> result = new ArrayList<Discussion>();
		for (Discussion discussion : sorted) {
			result.add(discussion);
		}
		return result;
	}
}
