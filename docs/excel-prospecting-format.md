# DraftWA Excel prospecting format

The first four columns are mandatory and ordered:

1. prospect_id
2. nom
3. telephones
4. message

Multiple phone numbers are separated with `/`. DraftWA normalizes whitespace, `+`, dashes and parentheses before analysis, skips Togo fixed-line numbers in the 22 range, tests candidates in order, and stops testing once a usable WhatsApp conversation is found.

Queue progress is stored locally by DraftWA rather than written back to the source spreadsheet.
