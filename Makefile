ROOT = $PWD

.PHONY gen_mavlink
	python -m pymavlink.tools.mavgen --lang=Java --wire-protocol=2.0 --output=$PWD/app/src/main/java/com/MAVLink/ lib/mavlink/message_definitions/v1.0/tamerlanchik.xml