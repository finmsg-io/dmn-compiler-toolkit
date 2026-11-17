// Unit tests for FEEL regex matcher (matches and replace)

mod regex_engine {
    #[derive(Clone, Debug)]
    pub enum AST {
        Empty,
        Char(char),
        Any,
        Class {
            negated: bool,
            ranges: Vec<(char, char)>,
            chars: Vec<char>,
            sub: Option<Box<AST>>,
        },
        AnchorStart,
        AnchorEnd,
        Backref(usize),
        Group(usize, Box<AST>),
        NonCapturing(Box<AST>),
        Concat(Vec<AST>),
        Alt(Vec<AST>),
        Repeat {
            inner: Box<AST>,
            min: usize,
            max: Option<usize>,
            greedy: bool,
        },
    }

    #[derive(Clone, Copy, Debug, Default)]
    pub struct Flags {
        pub i: bool, // case insensitive
        pub m: bool, // multiline
        pub s: bool, // dot matches all
        pub x: bool, // extended (ignore whitespace)
        pub q: bool, // literal quote
    }

    pub fn parse_flags(f: &str) -> Option<Flags> {
        let mut flags = Flags::default();
        for c in f.chars() {
            match c {
                'i' => flags.i = true,
                'm' => flags.m = true,
                's' => flags.s = true,
                'x' => flags.x = true,
                'q' => flags.q = true,
                _ => return None, // invalid flag
            }
        }
        Some(flags)
    }

    struct Parser {
        chars: Vec<char>,
        pos: usize,
        flags: Flags,
        group_count: usize,
    }

    impl Parser {
        fn new(pattern: &str, flags: Flags) -> Self {
            let mut chars = Vec::new();
            if flags.x {
                let mut in_class = false;
                for c in pattern.chars() {
                    if c == '[' {
                        in_class = true;
                        chars.push(c);
                    } else if c == ']' {
                        in_class = false;
                        chars.push(c);
                    } else if in_class || !c.is_whitespace() {
                        chars.push(c);
                    }
                }
            } else {
                chars = pattern.chars().collect();
            }
            Self {
                chars,
                pos: 0,
                flags,
                group_count: 0,
            }
        }

        fn peek(&self) -> Option<char> {
            self.chars.get(self.pos).copied()
        }

        fn next(&mut self) -> Option<char> {
            let c = self.chars.get(self.pos).copied();
            if c.is_some() {
                self.pos += 1;
            }
            c
        }

        fn parse(&mut self) -> Result<AST, ()> {
            let res = self.parse_alt()?;
            if self.pos < self.chars.len() {
                return Err(());
            }
            Ok(res)
        }

        fn parse_alt(&mut self) -> Result<AST, ()> {
            let mut branches = vec![self.parse_concat()?];
            while self.peek() == Some('|') {
                self.next();
                branches.push(self.parse_concat()?);
            }
            if branches.len() == 1 {
                Ok(branches.pop().unwrap())
            } else {
                Ok(AST::Alt(branches))
            }
        }

        fn parse_concat(&mut self) -> Result<AST, ()> {
            let mut pieces = Vec::new();
            while let Some(c) = self.peek() {
                if c == '|' || c == ')' {
                    break;
                }
                pieces.push(self.parse_quantified()?);
            }
            if pieces.is_empty() {
                Ok(AST::Empty)
            } else if pieces.len() == 1 {
                Ok(pieces.pop().unwrap())
            } else {
                Ok(AST::Concat(pieces))
            }
        }

        fn parse_quantified(&mut self) -> Result<AST, ()> {
            let base = self.parse_atom()?;
            if let Some(c) = self.peek() {
                match c {
                    '*' => {
                        self.next();
                        let greedy = self.check_greedy();
                        Ok(AST::Repeat { inner: Box::new(base), min: 0, max: None, greedy })
                    }
                    '+' => {
                        self.next();
                        let greedy = self.check_greedy();
                        Ok(AST::Repeat { inner: Box::new(base), min: 1, max: None, greedy })
                    }
                    '?' => {
                        self.next();
                        let greedy = self.check_greedy();
                        Ok(AST::Repeat { inner: Box::new(base), min: 0, max: Some(1), greedy })
                    }
                    '{' => {
                        self.next();
                        let (min, max) = self.parse_range()?;
                        let greedy = self.check_greedy();
                        Ok(AST::Repeat { inner: Box::new(base), min, max, greedy })
                    }
                    _ => Ok(base),
                }
            } else {
                Ok(base)
            }
        }

        fn check_greedy(&mut self) -> bool {
            if self.peek() == Some('?') {
                self.next();
                false
            } else {
                true
            }
        }

        fn parse_range(&mut self) -> Result<(usize, Option<usize>), ()> {
            let mut min_str = String::new();
            while let Some(c) = self.peek() {
                if c.is_ascii_digit() {
                    min_str.push(c);
                    self.next();
                } else {
                    break;
                }
            }
            let min: usize = min_str.parse().map_err(|_| ())?;
            if self.peek() == Some('}') {
                self.next();
                return Ok((min, Some(min)));
            }
            if self.peek() == Some(',') {
                self.next();
                if self.peek() == Some('}') {
                    self.next();
                    return Ok((min, None));
                }
                let mut max_str = String::new();
                while let Some(c) = self.peek() {
                    if c.is_ascii_digit() {
                        max_str.push(c);
                        self.next();
                    } else {
                        break;
                    }
                }
                let max: usize = max_str.parse().map_err(|_| ())?;
                if self.peek() == Some('}') {
                    self.next();
                    return Ok((min, Some(max)));
                }
            }
            Err(())
        }

        fn parse_atom(&mut self) -> Result<AST, ()> {
            let c = self.next().ok_or(())?;
            match c {
                '.' => Ok(AST::Any),
                '^' => Ok(AST::AnchorStart),
                '$' => Ok(AST::AnchorEnd),
                '(' => {
                    if self.peek() == Some('?') {
                        self.next();
                        if self.peek() == Some(':') {
                            self.next();
                            let inner = self.parse_alt()?;
                            if self.next() != Some(')') {
                                return Err(());
                            }
                            Ok(AST::NonCapturing(Box::new(inner)))
                        } else {
                            Err(())
                        }
                    } else {
                        self.group_count += 1;
                        let idx = self.group_count;
                        let inner = self.parse_alt()?;
                        if self.next() != Some(')') {
                            return Err(());
                        }
                        Ok(AST::Group(idx, Box::new(inner)))
                    }
                }
                '[' => self.parse_class(),
                '\\' => {
                    let esc = self.next().ok_or(())?;
                    match esc {
                        'd' => Ok(AST::Class {
                            negated: false,
                            ranges: vec![('0', '9')],
                            chars: vec![],
                            sub: None,
                        }),
                        's' => Ok(AST::Class {
                            negated: false,
                            ranges: vec![],
                            chars: vec![' ', '\t', '\n', '\r'],
                            sub: None,
                        }),
                        'w' => Ok(AST::Class {
                            negated: false,
                            ranges: vec![('a', 'z'), ('A', 'Z'), ('0', '9')],
                            chars: vec!['_'],
                            sub: None,
                        }),
                        '0' => Ok(AST::Char('\0')),
                        'p' => {
                            if self.next() != Some('{') {
                                return Err(());
                            }
                            let mut prop = String::new();
                            while let Some(ch) = self.next() {
                                if ch == '}' {
                                    break;
                                }
                                prop.push(ch);
                            }
                            let trimmed = prop.trim();
                            if !self.flags.x && (prop.starts_with(' ') || prop.ends_with(' ')) {
                                return Err(());
                            }
                            let compact: String = trimmed.chars().filter(|c| !c.is_whitespace()).collect();
                            if compact == "IsBasicLatin" || compact == "BasicLatin" {
                                Ok(AST::Class {
                                    negated: false,
                                    ranges: vec![('\0', '\u{7f}')],
                                    chars: vec![],
                                    sub: None,
                                })
                            } else {
                                Err(())
                            }
                        }
                        '1'..='9' => {
                            let idx = (esc as u8 - b'0') as usize;
                            if idx > self.group_count {
                                return Err(());
                            }
                            Ok(AST::Backref(idx))
                        }
                        other => Ok(AST::Char(other)),
                    }
                }
                other => Ok(AST::Char(other)),
            }
        }

        fn parse_class(&mut self) -> Result<AST, ()> {
            let negated = if self.peek() == Some('^') {
                self.next();
                true
            } else {
                false
            };
            let mut ranges = Vec::new();
            let mut chars = Vec::new();
            let mut sub = None;

            while let Some(c) = self.next() {
                if c == ']' {
                    return Ok(AST::Class { negated, ranges, chars, sub });
                }
                if c == '-' && self.peek() == Some('[') {
                    // Character class subtraction: -[...]
                    self.next(); // consume '['
                    let sub_ast = self.parse_class()?;
                    sub = Some(Box::new(sub_ast));
                    if self.next() != Some(']') {
                        return Err(());
                    }
                    return Ok(AST::Class { negated, ranges, chars, sub });
                }
                let ch = if c == '\\' {
                    let esc = self.next().ok_or(())?;
                    match esc {
                        '0' => return Err(()), // backreference/null in char class disallowed in TCK
                        '1'..='9' => return Err(()), // backreference in char class disallowed
                        'd' => {
                            ranges.push(('0', '9'));
                            continue;
                        }
                        's' => {
                            chars.extend(&[' ', '\t', '\n', '\r']);
                            continue;
                        }
                        other => other,
                    }
                } else {
                    c
                };

                if self.peek() == Some('-') {
                    // Check if range or trailing '-'
                    let next_pos = self.pos;
                    if self.chars.get(next_pos + 1) == Some(&']') || self.chars.get(next_pos + 1) == Some(&'[') {
                        // Trailing or subtraction prefix
                        chars.push(ch);
                    } else {
                        self.next(); // consume '-'
                        let end_c = self.next().ok_or(())?;
                        let end_ch = if end_c == '\\' {
                            self.next().ok_or(())?
                        } else {
                            end_c
                        };
                        ranges.push((ch, end_ch));
                    }
                } else {
                    chars.push(ch);
                }
            }
            Err(())
        }
    }

    #[derive(Clone, Debug)]
    struct State {
        groups: Vec<Option<(usize, usize)>>,
    }

    fn char_eq(c1: char, c2: char, ignore_case: bool) -> bool {
        if c1 == c2 {
            return true;
        }
        if ignore_case {
            c1.to_lowercase().eq(c2.to_lowercase())
        } else {
            false
        }
    }

    fn matches_char_class(c: char, ast: &AST, ignore_case: bool) -> bool {
        let AST::Class { negated, ranges, chars, sub } = ast else { return false };
        let mut hit = false;
        for (start, end) in ranges {
            if ignore_case {
                let cl = c.to_lowercase().next().unwrap_or(c);
                let sl = start.to_lowercase().next().unwrap_or(*start);
                let el = end.to_lowercase().next().unwrap_or(*end);
                if (c >= *start && c <= *end) || (cl >= sl && cl <= el) {
                    hit = true;
                    break;
                }
            } else if c >= *start && c <= *end {
                hit = true;
                break;
            }
        }
        if !hit {
            for &ch in chars {
                if char_eq(c, ch, ignore_case) {
                    hit = true;
                    break;
                }
            }
        }
        let in_base = if *negated { !hit } else { hit };
        if !in_base {
            return false;
        }
        if let Some(sub_class) = sub {
            if matches_char_class(c, sub_class, ignore_case) {
                return false;
            }
        }
        true
    }

    fn match_node<'a>(
        ast: &AST,
        input: &[char],
        pos: usize,
        state: &mut State,
        flags: Flags,
    ) -> Vec<(usize, State)> {
        match ast {
            AST::Empty => vec![(pos, state.clone())],
            AST::Char(ch) => {
                if pos < input.len() && char_eq(input[pos], *ch, flags.i) {
                    vec![(pos + 1, state.clone())]
                } else {
                    vec![]
                }
            }
            AST::Any => {
                if pos < input.len() {
                    let c = input[pos];
                    if flags.s || (c != '\n' && c != '\r') {
                        vec![(pos + 1, state.clone())]
                    } else {
                        vec![]
                    }
                } else {
                    vec![]
                }
            }
            AST::Class { .. } => {
                if pos < input.len() && matches_char_class(input[pos], ast, flags.i) {
                    vec![(pos + 1, state.clone())]
                } else {
                    vec![]
                }
            }
            AST::AnchorStart => {
                let ok = if flags.m {
                    pos == 0 || (pos > 0 && input[pos - 1] == '\n')
                } else {
                    pos == 0
                };
                if ok {
                    vec![(pos, state.clone())]
                } else {
                    vec![]
                }
            }
            AST::AnchorEnd => {
                let ok = if flags.m {
                    pos == input.len() || (pos < input.len() && input[pos] == '\n')
                } else {
                    pos == input.len()
                };
                if ok {
                    vec![(pos, state.clone())]
                } else {
                    vec![]
                }
            }
            AST::Backref(idx) => {
                if let Some(Some((start, end))) = state.groups.get(*idx) {
                    let ref_slice = &input[*start..*end];
                    if pos + ref_slice.len() <= input.len() {
                        let mut ok = true;
                        for i in 0..ref_slice.len() {
                            if !char_eq(input[pos + i], ref_slice[i], flags.i) {
                                ok = false;
                                break;
                            }
                        }
                        if ok {
                            vec![(pos + ref_slice.len(), state.clone())]
                        } else {
                            vec![]
                        }
                    } else {
                        vec![]
                    }
                } else {
                    vec![]
                }
            }
            AST::Group(idx, inner) => {
                let mut results = Vec::new();
                for (next_pos, mut next_state) in match_node(inner, input, pos, state, flags) {
                    if *idx >= next_state.groups.len() {
                        next_state.groups.resize(*idx + 1, None);
                    }
                    next_state.groups[*idx] = Some((pos, next_pos));
                    results.push((next_pos, next_state));
                }
                results
            }
            AST::NonCapturing(inner) => match_node(inner, input, pos, state, flags),
            AST::Concat(pieces) => {
                let mut current = vec![(pos, state.clone())];
                for piece in pieces {
                    let mut next = Vec::new();
                    for (cur_pos, mut cur_state) in current {
                        next.extend(match_node(piece, input, cur_pos, &mut cur_state, flags));
                    }
                    if next.is_empty() {
                        return vec![];
                    }
                    current = next;
                }
                current
            }
            AST::Alt(branches) => {
                let mut results = Vec::new();
                for b in branches {
                    let res = match_node(b, input, pos, state, flags);
                    if !res.is_empty() {
                        results.extend(res);
                        // In regex alternations, first match is preferred
                        break;
                    }
                }
                results
            }
            AST::Repeat { inner, min, max, greedy } => {
                let mut results = Vec::new();
                match_repeat(inner, *min, *max, *greedy, input, pos, state, flags, 0, &mut results);
                results
            }
        }
    }

    fn match_repeat(
        inner: &AST,
        min: usize,
        max: Option<usize>,
        greedy: bool,
        input: &[char],
        pos: usize,
        state: &State,
        flags: Flags,
        count: usize,
        results: &mut Vec<(usize, State)>,
    ) {
        if let Some(m) = max {
            if count > m {
                return;
            }
        }
        let can_stop = count >= min;
        if !greedy && can_stop {
            results.push((pos, state.clone()));
            if max == Some(count) {
                return;
            }
        }

        let at_limit = max == Some(count);
        if !at_limit {
            let next_matches = match_node(inner, input, pos, &mut state.clone(), flags);
            for (next_pos, next_state) in next_matches {
                if next_pos == pos {
                    // avoid infinite loop on empty match
                    continue;
                }
                match_repeat(inner, min, max, greedy, input, next_pos, &next_state, flags, count + 1, results);
            }
        }

        if greedy && can_stop {
            results.push((pos, state.clone()));
        }
    }

    pub fn feel_matches(input: &str, pattern: &str, flags_str: &str) -> Option<bool> {
        let flags = parse_flags(flags_str)?;
        if flags.q {
            if flags.i {
                return Some(input.to_lowercase().contains(&pattern.to_lowercase()));
            } else {
                return Some(input.contains(pattern));
            }
        }
        let mut parser = Parser::new(pattern, flags);
        let ast = parser.parse().ok()?;
        let chars: Vec<char> = input.chars().collect();
        for start in 0..=chars.len() {
            let mut state = State { groups: vec![None; parser.group_count + 1] };
            let matches = match_node(&ast, &chars, start, &mut state, flags);
            if !matches.is_empty() {
                return Some(true);
            }
        }
        Some(false)
    }

    pub fn feel_replace(input: &str, pattern: &str, replacement: &str, flags_str: &str) -> Option<String> {
        let flags = parse_flags(flags_str)?;
        if flags.q {
            if flags.i {
                // simple case-insensitive literal replace
                let mut res = String::new();
                let lower_input = input.to_lowercase();
                let lower_pat = pattern.to_lowercase();
                let mut last = 0;
                while let Some(idx) = lower_input[last..].find(&lower_pat) {
                    res.push_str(&input[last..last + idx]);
                    res.push_str(replacement);
                    last += idx + pattern.len();
                }
                res.push_str(&input[last..]);
                return Some(res);
            } else {
                return Some(input.replace(pattern, replacement));
            }
        }
        let mut parser = Parser::new(pattern, flags);
        let ast = parser.parse().ok()?;
        let chars: Vec<char> = input.chars().collect();
        let mut pos = 0;
        let mut out = String::new();

        while pos <= chars.len() {
            let mut state = State { groups: vec![None; parser.group_count + 1] };
            let matches = match_node(&ast, &chars, pos, &mut state, flags);
            if let Some((end_pos, match_state)) = matches.into_iter().next() {
                // Expand replacement
                let mut rep = String::new();
                let mut r_chars = replacement.chars().peekable();
                while let Some(rc) = r_chars.next() {
                    if rc == '$' {
                        if let Some(&next_c) = r_chars.peek() {
                            if next_c.is_ascii_digit() {
                                r_chars.next();
                                let grp_idx = (next_c as u8 - b'0') as usize;
                                if grp_idx == 0 {
                                    rep.extend(&chars[pos..end_pos]);
                                } else if let Some(Some((g_start, g_end))) = match_state.groups.get(grp_idx) {
                                    rep.extend(&chars[*g_start..*g_end]);
                                }
                                continue;
                            }
                        }
                        rep.push('$');
                    } else {
                        rep.push(rc);
                    }
                }
                out.push_str(&rep);
                if end_pos > pos {
                    pos = end_pos;
                } else {
                    if pos < chars.len() {
                        out.push(chars[pos]);
                        pos += 1;
                    } else {
                        break;
                    }
                }
            } else {
                if pos < chars.len() {
                    out.push(chars[pos]);
                    pos += 1;
                } else {
                    break;
                }
            }
        }
        Some(out)
    }
}

#[cfg(test)]
mod tests {
    use super::regex_engine::*;

    #[test]
    fn test_all_feel_replace_cases() {
        assert_eq!(feel_replace("abcd", "(ab)|(a)", "[1=$1][2=$2]", "").unwrap(), "[1=ab][2=]cd");
        assert_eq!(feel_replace("a", "[b-z]", "#", "").unwrap(), "a");
        assert_eq!(feel_replace("a", "[a-z]", "#", "").unwrap(), "#");
        assert_eq!(feel_replace("abc", "def", "#", "").unwrap(), "abc");
        assert_eq!(feel_replace("abc", "e", "#", "").unwrap(), "abc");
        assert_eq!(feel_replace("foobar", "^fo*b*", "#", "").unwrap(), "#ar");
        assert_eq!(feel_replace("abc", ".^[d-z]", "#", "").unwrap(), "abc");
        assert_eq!(feel_replace("abracadabra", "bra", "*", "").unwrap(), "a*cada*");
        assert_eq!(feel_replace("abracadabra", "a.*a", "*", "").unwrap(), "*");
        assert_eq!(feel_replace("abracadabra", "a.*?a", "*", "").unwrap(), "*c*bra");
        assert_eq!(feel_replace("abracadabra", "a", "", "").unwrap(), "brcdbr");
        assert_eq!(feel_replace("abracadabra", "a(.)", "a$1$1", "").unwrap(), "abbraccaddabbra");
        assert_eq!(feel_replace("AAAA", "A+", "b", "").unwrap(), "b");
        assert_eq!(feel_replace("AAAA", "A+?", "b", "").unwrap(), "bbbb");
        assert_eq!(feel_replace("darted", "^(.*?)d(.*)$", "$1c$2", "").unwrap(), "carted");
        assert_eq!(feel_replace("reluctant", "r.*?t", "X", "").unwrap(), "Xant");
        assert_eq!(feel_replace("0123456789", r"(\d{3})(\d{3})(\d{4})", "($1) $2-$3", "").unwrap(), "(012) 345-6789");
        assert_eq!(feel_replace("facetiously", "[iouy]", "[$0]", "").unwrap(), "facet[i][o][u]sl[y]");
        assert_eq!(feel_replace("abc", "[a-z]", "#", "").unwrap(), "###");
        assert_eq!(feel_replace("a.b.c.", "[a-z]", "#", "s").unwrap(), "#.#.#.");
        assert_eq!(feel_replace("abc", "[A-Z]", "#", "i").unwrap(), "###");
        assert_eq!(feel_replace("abc", "[a-z]", "#", "s").unwrap(), "###");
        assert_eq!(feel_replace("a b c d ", "[a-z]", "#", "x").unwrap(), "# # # # ");
        assert_eq!(feel_replace("abc", ".^[d-z]*", "#", "smix").unwrap(), "abc");
    }

    #[test]
    fn test_feel_matches_cases() {
        assert_eq!(feel_matches("This is a characte", "This is a characte", "").unwrap(), true);
        assert_eq!(feel_matches("abracadabra", "bra", "p"), None);
        assert_eq!(feel_matches("\nabcd\ndefg\n", "^$", "m").unwrap(), true);
        assert_eq!(feel_matches("abcd\n\ndefg\n ", "^$", "m").unwrap(), true);
        assert_eq!(feel_matches("Mary\rJones", "Mary.Jones", "").unwrap(), false);
        assert_eq!(feel_matches("abc", "ABC", "i").unwrap(), true);
        assert_eq!(feel_matches("abZ", "[A-Z]*", "i").unwrap(), true);
        assert_eq!(feel_matches("abZ", "[a-z]*", "i").unwrap(), true);
        assert_eq!(feel_matches("\u{212A}", "k", "i").unwrap(), true);
        assert_eq!(feel_matches("x", "[A-Z-[OI]]", "i").unwrap(), true);
        assert_eq!(feel_matches("X", "[A-Z-[OI]]", "i").unwrap(), true);
        assert_eq!(feel_matches("O", "[A-Z-[OI]]", "i").unwrap(), false);
        assert_eq!(feel_matches("i", "[A-Z-[OI]]", "i").unwrap(), false);
        assert_eq!(feel_matches("Q", "[^Q]", "i").unwrap(), false);
        assert_eq!(feel_matches("q", "[^Q]", "i").unwrap(), false);
        assert_eq!(feel_matches("hello world", "hello\\ sworld", "x").unwrap(), true);
        assert_eq!(feel_matches("hello world", " hello[ ]world", "x").unwrap(), true);
        assert_eq!(feel_matches("hello world", "hello[ ]world ", "x").unwrap(), true);
        assert_eq!(feel_matches("hello world", "he ll o[ ]worl d", "x").unwrap(), true);
        assert_eq!(feel_matches("hello world", "\\p{ IsBasicLatin}+", "x").unwrap(), true);
        assert_eq!(feel_matches("hello world", "\\p{ I s B a s i c L a t i n }+", "x").unwrap(), true);
        assert_eq!(feel_matches("hello world", "\\p{ IsBasicLatin}+", ""), None);
        assert_eq!(feel_matches("h", "(.)\\3", ""), None);
        assert_eq!(feel_matches("h", "(.)\\2", ""), None);
        assert_eq!(feel_matches("input", "\\3", ""), None);
        assert_eq!(feel_matches("abcd", "(asd)[\\1]", ""), None);
        assert_eq!(feel_matches("abcd", "(asd)[asd\\1]", ""), None);
        assert_eq!(feel_matches("abcd", "(asd)[asd\\0]", ""), None);
        assert_eq!(feel_matches("abcd", "1[asd\\0]", ""), None);
        assert_eq!(feel_matches("aA", "(a)\\1", "i").unwrap(), true);
    }
}
