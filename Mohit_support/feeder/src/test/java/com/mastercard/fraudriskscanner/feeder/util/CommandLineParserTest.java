package com.mastercard.fraudriskscanner.feeder.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CommandLineParser utility class.
 */
class CommandLineParserTest {

	@Test
	void testParseSourceDirectories_NullArgs() {
		String result = CommandLineParser.parseSourceDirectories(null);
		assertNull(result);
	}

	@Test
	void testParseSourceDirectories_EmptyArgs() {
		String result = CommandLineParser.parseSourceDirectories(new String[0]);
		assertNull(result);
	}

	@Test
	void testParseSourceDirectories_PositionalArgument() {
		String[] args = {"/path/to/directory"};
		String result = CommandLineParser.parseSourceDirectories(args);
		assertEquals("/path/to/directory", result);
	}

	@Test
	void testParseSourceDirectories_PositionalArgumentWithMultipleDirs() {
		String[] args = {"/path/to/dir1,/path/to/dir2"};
		String result = CommandLineParser.parseSourceDirectories(args);
		assertEquals("/path/to/dir1,/path/to/dir2", result);
	}

	@Test
	void testParseSourceDirectories_WithFlag() {
		String[] args = {"--source-directories", "/path/to/directory"};
		String result = CommandLineParser.parseSourceDirectories(args);
		assertEquals("/path/to/directory", result);
	}

	@Test
	void testParseSourceDirectories_WithShortFlag() {
		String[] args = {"-d", "/path/to/directory"};
		String result = CommandLineParser.parseSourceDirectories(args);
		assertEquals("/path/to/directory", result);
	}

	@Test
	void testParseSourceDirectories_WithFlagAndMultipleDirs() {
		String[] args = {"--source-directories", "/path/to/dir1,/path/to/dir2"};
		String result = CommandLineParser.parseSourceDirectories(args);
		assertEquals("/path/to/dir1,/path/to/dir2", result);
	}

	@Test
	void testParseSourceDirectories_FlagWithoutValue() {
		String[] args = {"--source-directories"};
		String result = CommandLineParser.parseSourceDirectories(args);
		assertNull(result);
	}

	@Test
	void testParseSourceDirectories_ShortFlagWithoutValue() {
		String[] args = {"-d"};
		String result = CommandLineParser.parseSourceDirectories(args);
		assertNull(result);
	}

	@Test
	void testParseSourceDirectories_OtherFlagsIgnored() {
		String[] args = {"--other-flag", "value", "/path/to/directory"};
		String result = CommandLineParser.parseSourceDirectories(args);
		// Should use positional argument
		assertEquals("/path/to/directory", result);
	}

	@Test
	void testParseSourceDirectories_FlagTakesPrecedenceOverPositional() {
		String[] args = {"--source-directories", "/flag/path", "/positional/path"};
		String result = CommandLineParser.parseSourceDirectories(args);
		// Flag should take precedence
		assertEquals("/flag/path", result);
	}

	@Test
	void testParseSourceDirectories_StartsWithDashNotPositional() {
		String[] args = {"--unknown-flag"};
		String result = CommandLineParser.parseSourceDirectories(args);
		assertNull(result);
	}

	@Test
	void testParseSourceDirectories_MultipleFlags() {
		String[] args = {"--source-directories", "/first/path", "--source-directories", "/second/path"};
		String result = CommandLineParser.parseSourceDirectories(args);
		// Should return first match
		assertEquals("/first/path", result);
	}

	@Test
	void testParseSourceDirectories_EmptyStringValue() {
		String[] args = {"--source-directories", ""};
		String result = CommandLineParser.parseSourceDirectories(args);
		assertEquals("", result);
	}
}

