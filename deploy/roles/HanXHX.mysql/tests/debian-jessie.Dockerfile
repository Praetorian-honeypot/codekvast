FROM williamyeh/ansible:debian8-onbuild

RUN apt-get update
RUN apt-get install -y python-netaddr
CMD ["sh", "tests/travis.sh"]
