--changeset elhub:1
create table example_table (
  id int primary key,
  firstname varchar(50),
  lastname varchar(50) not null,
  state char(2)
)
