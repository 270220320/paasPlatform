#!/usr/bin/env python3
"""Generate a Spring-Security-compatible ($2a$) BCrypt hash for a given password.

Usage: python3 _gen_bcrypt.py <password>
Prints the hash to stdout. Also self-verifies before printing.
"""
import sys

import bcrypt

pwd = sys.argv[1].encode()
h = bcrypt.hashpw(pwd, bcrypt.gensalt(rounds=10)).decode()
# python-bcrypt emits $2b$; Spring Security's BCryptPasswordEncoder expects $2a$.
# For ASCII passwords both versions produce identical digests.
h = h.replace("$2b$", "$2a$", 1)
assert bcrypt.checkpw(pwd, h.encode()), "self-verification failed"
print(h)
