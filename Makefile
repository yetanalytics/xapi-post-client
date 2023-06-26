clean:
	rm -rf target

test-unit:
	clj -X:test :dirs '["src/test"]'
