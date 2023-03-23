profile?=1.0

all: build release

build: js test lint bin

js: bower_components resources/public/js/core.js
	node_modules/.bin/gulp

node_modules: package.json
	npm install

bower_components: node_modules bower.json
	node_modules/.bin/bower install

bin:
	lein with-profile $(profile) bin

release:
	mkdir -p releases/
	cp -v target/$(profile)/hbase-region-inspector-* releases/

repl:
	lein with-profile $(profile),$(profile)-test repl :connect localhost:9999

lint: cljlint jslint


cljlint:
ifeq ($(profile), cdh4)
		lein with-profile $(profile),$(profile)-test eastwood "{:exclude-linters [:deprecations :reflection]}"
else
		lein with-profile $(profile),$(profile)-test eastwood "{:exclude-linters [:deprecations]}"
endif

resources/public/js/core.js: resources/jsx/core.jsx
	node_modules/.bin/babel resources/jsx/core.jsx --out-file $@

jslint: js
	node_modules/.bin/standard resources/jsx/core.jsx

serve: js
	-killall -9 babel
	node_modules/.bin/babel --watch resources/jsx/ --out-file resources/public/js/core.js &
	DEBUG=1 lein with-profile $(profile),$(profile)-test,user ring server-headless

test:
	lein with-profile $(profile),$(profile)-test test

autotest:
	# https://github.com/jakemcc/lein-test-refresh/issues/35
	lein with-profile $(profile),$(profile)-test,user test-refresh

doc:
	lein with-profile $(profile) doc

.PHONY: all build bin release js repl lint cljlint jslint serve doc test autotest
