package br.com.phdigitalcode.azzo.agenda.pro.entity;

import br.com.phdigitalcode.azzo.agenda.pro.entity.id.RbacRolePermissionId;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Espelha {@code modules/auth/domain/entity/RbacRolePermission.java}. Tabela {@code role_permissions}. */
@Entity
@Table(name = "role_permissions")
@Getter
@Setter
public class RbacRolePermission {

  @EmbeddedId
  private RbacRolePermissionId id;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RbacRolePermission other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
