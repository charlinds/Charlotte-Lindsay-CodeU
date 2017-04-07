// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.codeu.codingchallenge;

import java.io.IOException;
import java.util.*;


final class MyJSONParser implements JSONParser {

    private static final int JSON_STRING = 1;
    private static final int SYMBOL = 2;
    private static final Terminal OPENING_BRACE = new Terminal(SYMBOL, "{");
    private static final Terminal CLOSING_BRACE = new Terminal(SYMBOL, "}");
    private static final Terminal COMMA = new Terminal(SYMBOL, ",");
    private static final Terminal COLON = new Terminal(SYMBOL, ":");

    // A Terminal can be either a JSON String or a special character symbol, which is one of { } , :
    private static final class Terminal {
        public final int type;  // 1 = JSON STRING, 2 = SYMBOL
        public final String value;

        public Terminal(int type, String value) {

            this.type = type;
            this.value = value;
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Terminal terminal = (Terminal) o;

            if (type != terminal.type) return false;
            return value.equals(terminal.value);

        }

        @Override
        public int hashCode() {
            int result = type;
            result = 31 * result + value.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Terminal{" +
                    "type=" + type +
                    ", value='" + value + '\'' +
                    '}';
        }
    }

    private static final class TerminalSequenceGenerator {
        public TerminalSequenceGenerator(String input) {
            this.input = input;
            this.terminalSequence = new ArrayList<>();
        }

        private String input;

        // analyse the input string into a list of Terminals.
        // returns false if errors are encountered while doing so.
        public boolean analyse() {
            int i = 0;
            while (i < input.length()) {
                char c = input.charAt(i);
                switch (c) {
                    case '{':
                        this.terminalSequence.add(OPENING_BRACE);
                        break;
                    case '}':
                        this.terminalSequence.add(CLOSING_BRACE);
                        break;
                    case ':':
                        this.terminalSequence.add(COLON);
                        break;
                    case ',':
                        this.terminalSequence.add(COMMA);
                        break;
                    case '"':
                        // see the start of a JSON string.
                        // This case looks forward into the input character by character and builds the string.
                        StringBuilder stringBuilder = new StringBuilder();
                        boolean stringCompleted = false;
                        while (!stringCompleted) {
                            i++;
                            if (i == input.length()) {
                                return false;
                            }
                            char c2 = input.charAt(i);
                            switch (c2) {
                                case '"':
                                    // ok, end of string, create terminal and break to main while loop
                                    stringCompleted = true;
                                    this.terminalSequence.add(new Terminal(JSON_STRING, stringBuilder.toString()));
                                    break;
                                case '\\':
                                    // \ is the escape character.
                                    // 4 possible escape sequences -> \" \\ \t and \n
                                    // in each case, add the escape sequence to the string builder
                                    i++;
                                    if (i == input.length()) {
                                        return false;
                                    }
                                    char c3 = input.charAt(i);

                                    switch (c3) {
                                        case '"':
                                            stringBuilder.append(c3);
                                            break;
                                        case '\\':
                                            stringBuilder.append(c3);
                                            break;
                                        case 't':
                                            stringBuilder.append('\t');
                                            break;
                                        case 'n':
                                            stringBuilder.append('\n');
                                            break;
                                        default:
                                            return false;
                                    }
                                    break;
                                default:
                                    // not a special character, just add to the builder
                                    stringBuilder.append(c2);
                                    break;
                            }
                        }
                        break;
                    default:
                        if (Character.isWhitespace(c)) {
                            // skip whitespace characters
                            break;
                        } else {
                            // unexpected character, return false
                            return false;
                        }
                }
                i++;
            }
            return true;
        }

        public List<Terminal> getTerminalSequence() {
            return terminalSequence;
        }

        private List<Terminal> terminalSequence;

    }

    // parses the sequence of Terminals, according to the "JSON-lite" parsing rules:

    /*
     *
     *  object
     *      {}
     *      { pairs }
     *
     *  pairs
     *      pair
     *      pair , pair
     *
     *  pair
     *      string : value
     *
     *  value
     *      string
     *      object
     */

    // the parser for the Terminal Sequence
    private static final class TerminalSequenceParser {

        public TerminalSequenceParser(List<Terminal> terminalSequence) {

            this.terminalSequence = terminalSequence;

        }

        private final List<Terminal> terminalSequence;
        private int terminalSequencePosition = -1;


        public boolean setStartingPosition() {
            if (terminalSequence.size() >= 1) {
                this.terminalSequencePosition = 0;
                return true;
            } else {
                return false;
            }
        }

        public boolean positionAfterEnd() {
            return this.terminalSequencePosition == this.terminalSequence.size();
        }

        int getTerminalSequencePosition() {
            return this.terminalSequencePosition;
        }

        // used to restore the terminal position in case of a parsing rule failure
        void restore(int terminalSequencePosition) {
            this.terminalSequencePosition = terminalSequencePosition;
        }

        boolean forwardTerminalSequencePosition() {
            terminalSequencePosition++;

            if (terminalSequencePosition >= terminalSequence.size()) {
                return false;
            }

            return true;
        }

        Terminal getCurrentTerminal() {
            return this.terminalSequence.get(this.terminalSequencePosition);
        }

        // just a temporary holder of JSON data
        static class JsonHolder {
            public String jsonString;
            public JSON jsonObject;
        }

        // checks that the terminal is the current terminal and then forwards the position
        boolean accept(Terminal terminal) {
            if (this.getCurrentTerminal().equals(terminal)) {
                forwardTerminalSequencePosition();
                return true;
            } else {
                return false;
            }
        }

        // parsing rule for KeyValuePair(s). If success the key-value pairs are added to jsonObject
        boolean parseKeyValuePairs(JSON jsonObject) {

            int terminalSequencePosition = getTerminalSequencePosition();

            if (!parseKeyValuePair(jsonObject)) {
                restore(terminalSequencePosition);
                return false;
            }

            if (this.getCurrentTerminal().equals(COMMA)) {
                if (accept(COMMA) && parseKeyValuePairs(jsonObject)) {
                    return true;
                } else {
                    restore(terminalSequencePosition);
                    return false;
                }
            } else {
                return true;
            }

        }

        // parsing rule for a KeyValuePair. If success the key-value pair is added to jsonObject
        boolean parseKeyValuePair(JSON jsonObject) {

            int terminalSequencePosition = getTerminalSequencePosition();

            JsonHolder keyHolder = new JsonHolder();
            JsonHolder valueHolder = new JsonHolder();

            if (parseString(keyHolder) && accept(COLON) && parseValue(valueHolder)) {
                if (valueHolder.jsonObject != null) {
                    jsonObject.setObject(keyHolder.jsonString, valueHolder.jsonObject);
                } else {
                    jsonObject.setString(keyHolder.jsonString, valueHolder.jsonString);
                }
                return true;
            } else {
                restore(terminalSequencePosition);
                return false;
            }
        }

        // parsing rule for a JSON string. If success, the result is in holder.jsonString
        boolean parseString(JsonHolder holder) {
            int terminalSequencePosition = getTerminalSequencePosition();

            if (getCurrentTerminal().type == JSON_STRING) {
                holder.jsonString = getCurrentTerminal().value;
                accept(getCurrentTerminal());
                return true;
            }
            return false;
        }

        // parsing rule for a JSON value, which can be a JSON string or object. If success the appropriate field in the holder is populated
        boolean parseValue(JsonHolder holder) {
            int terminalSequencePosition = getTerminalSequencePosition();

            if (!parseString(holder)) {
                restore(terminalSequencePosition);
                terminalSequencePosition = getTerminalSequencePosition();
                JSON jsonObject = new MyJSON();
                if (!parseObject(jsonObject)) {
                    restore(terminalSequencePosition);
                    return false;
                } else {
                    holder.jsonObject = jsonObject;
                    return true;
                }
            } else {
                return true;
            }
        }

        // parsing rule for a JSON Object. If success then jsonObject is populated, else it is untouched
        boolean parseObject(JSON jsonObject) {

            int terminalSequencePosition = getTerminalSequencePosition();

            if (!accept(OPENING_BRACE)) {
                restore(terminalSequencePosition);
                return false;
            }

            if (this.getCurrentTerminal().equals(CLOSING_BRACE)) {
                accept(CLOSING_BRACE);
                return true;
            } else {
                if (parseKeyValuePairs(jsonObject) && accept(CLOSING_BRACE)) {
                    return true;
                } else {
                    restore(terminalSequencePosition);
                    return false;

                }
            }
        }

    }


    @Override
    // throws IO exceptions in each stage of the parsing
    public JSON parse(String in) throws IOException {


        // declare instance of TerminalSequenceGenerator
        final TerminalSequenceGenerator terminalSequenceGenerator = new TerminalSequenceGenerator(in);

        // analyse the input to generate Terminal Sequence
        if (!terminalSequenceGenerator.analyse()) {
            throw new IOException("Failed to analyse");
        }

        // declare instance of the TerminalSequenceParser
        final TerminalSequenceParser terminalSequenceParser = new TerminalSequenceParser(terminalSequenceGenerator.getTerminalSequence());

        // initialize the parser
        if (!terminalSequenceParser.setStartingPosition()) {
            throw new IOException("Could not set starting position");
        }

        JSON jsonObject = new MyJSON();

        // parse the Terminal Sequence into the jsonObject
        if (!terminalSequenceParser.parseObject(jsonObject)) {
            throw new IOException("Could not parse into JSON Object");
        }

        // check that all terminals in Sequence have been used
        if (!terminalSequenceParser.positionAfterEnd()) {
            throw new IOException("Additional terminals to parse");
        }

        return jsonObject;
    }
}
